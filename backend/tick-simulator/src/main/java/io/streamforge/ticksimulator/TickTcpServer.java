package io.streamforge.ticksimulator;

import io.streamforge.stp.protocol.StpEncoder;
import io.streamforge.stp.protocol.StpMessage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A single-writer TCP server that streams deterministic STP frames to each connected client.
 *
 * <p>Frames are encoded and written synchronously. A slow client therefore applies TCP backpressure
 * to generation instead of allowing an unbounded application queue to form.
 */
public final class TickTcpServer implements AutoCloseable {

  private final TickTcpServerConfig config;
  private final Consumer<String> reporter;
  private final AtomicBoolean running = new AtomicBoolean();
  private final CountDownLatch firstConnectionCompleted = new CountDownLatch(1);

  private volatile ServerSocket serverSocket;
  private volatile Socket activeSocket;
  private volatile Thread acceptThread;

  public TickTcpServer(TickTcpServerConfig config, Consumer<String> reporter) {
    this.config = Objects.requireNonNull(config, "config must not be null");
    this.reporter = Objects.requireNonNull(reporter, "reporter must not be null");
  }

  /** Binds the listening socket and starts accepting connections. */
  public synchronized void start() throws IOException {
    if (!running.compareAndSet(false, true)) {
      throw new IllegalStateException("server is already running");
    }

    try {
      ServerSocket socket = new ServerSocket();
      socket.bind(new InetSocketAddress(config.host(), config.port()));
      serverSocket = socket;
      acceptThread = Thread.ofPlatform().name("streamforge-tick-acceptor").start(this::acceptLoop);
      reporter.accept("listening on " + config.host() + ":" + socket.getLocalPort());
    } catch (IOException | RuntimeException error) {
      running.set(false);
      closeQuietly(serverSocket);
      serverSocket = null;
      throw error;
    }
  }

  /** Returns the bound port after {@link #start()} succeeds. */
  public int port() {
    ServerSocket socket = serverSocket;
    if (socket == null) {
      throw new IllegalStateException("server is not running");
    }
    return socket.getLocalPort();
  }

  /** Waits until the first accepted connection has completed streaming or failed. */
  public void awaitFirstConnectionCompletion() throws InterruptedException {
    firstConnectionCompleted.await();
  }

  private void acceptLoop() {
    while (running.get()) {
      try {
        Socket socket = serverSocket.accept();
        activeSocket = socket;
        try (socket) {
          reporter.accept("client connected: " + socket.getRemoteSocketAddress());
          streamEvents(socket.getOutputStream());
          reporter.accept("client stream completed: " + socket.getRemoteSocketAddress());
        } catch (IOException | IllegalStateException error) {
          if (running.get()) {
            reporter.accept("connection error: " + error.getMessage());
          }
        } finally {
          activeSocket = null;
          firstConnectionCompleted.countDown();
        }
      } catch (SocketException error) {
        if (running.get()) {
          reporter.accept("accept error: " + error.getMessage());
        }
      } catch (IOException error) {
        if (running.get()) {
          reporter.accept("accept error: " + error.getMessage());
        }
      }
    }
  }

  private void streamEvents(OutputStream output) throws IOException {
    StpTickEventGenerator generator = new StpTickEventGenerator(config.simulation());
    StpEncoder encoder = new StpEncoder();
    EventPacer pacer = new EventPacer(config.eventsPerSecond());
    while (running.get()) {
      StpMessage message = generator.next().orElse(null);
      if (message == null) {
        break;
      }
      output.write(encoder.encode(message));
      output.flush();
      pacer.awaitNext();
    }
  }

  @Override
  public void close() {
    if (!running.getAndSet(false)) {
      return;
    }
    firstConnectionCompleted.countDown();
    closeQuietly(activeSocket);
    closeQuietly(serverSocket);
    Thread thread = acceptThread;
    if (thread != null && thread != Thread.currentThread()) {
      try {
        thread.join();
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void closeQuietly(ServerSocket socket) {
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException ignored) {
        // Best-effort cleanup during startup rollback and shutdown.
      }
    }
  }

  private static void closeQuietly(Socket socket) {
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException ignored) {
        // Best-effort cleanup during shutdown.
      }
    }
  }

  private static final class EventPacer {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final long intervalNanos;
    private long nextDeadlineNanos;

    private EventPacer(long eventsPerSecond) {
      intervalNanos = eventsPerSecond == 0 ? 0 : NANOS_PER_SECOND / eventsPerSecond;
      if (eventsPerSecond > 0 && intervalNanos == 0) {
        throw new IllegalArgumentException("eventsPerSecond must not exceed 1000000000");
      }
      nextDeadlineNanos = System.nanoTime();
    }

    private void awaitNext() {
      if (intervalNanos == 0) {
        return;
      }
      nextDeadlineNanos += intervalNanos;
      long remainingNanos = nextDeadlineNanos - System.nanoTime();
      if (remainingNanos > 0) {
        java.util.concurrent.locks.LockSupport.parkNanos(remainingNanos);
      }
    }
  }
}
