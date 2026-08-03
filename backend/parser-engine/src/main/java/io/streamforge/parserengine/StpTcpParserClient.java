package io.streamforge.parserengine;

import io.streamforge.stp.protocol.IncrementalStpDecoder;
import io.streamforge.stp.protocol.ParsedStpFrame;
import io.streamforge.stp.protocol.StpParseEvent;
import io.streamforge.stp.protocol.StpProtocol;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Connects to one TCP STP stream and incrementally reports every parsed frame or parse failure. */
public final class StpTcpParserClient {

  public static final int DEFAULT_MAXIMUM_FRAME_SIZE =
      StpProtocol.LENGTH_FIELD_WIDTH + StpProtocol.MAX_ENCODED_LENGTH;
  private static final int READ_BUFFER_SIZE = 4_096;

  /** Parses one connection using the STP v1 maximum known frame size. */
  public StpParserClientResult parse(String host, int port, Consumer<StpParseEvent> eventConsumer)
      throws IOException {
    return parse(host, port, DEFAULT_MAXIMUM_FRAME_SIZE, true, eventConsumer);
  }

  /** Parses one connection with a caller-selected bounded incremental decoder capacity. */
  public StpParserClientResult parse(
      String host, int port, int maximumFrameSize, Consumer<StpParseEvent> eventConsumer)
      throws IOException {
    return parse(host, port, maximumFrameSize, true, eventConsumer);
  }

  /**
   * Parses one connection, optionally preserving sequence anomalies for an external tracker.
   *
   * <p>Framing and field validation always remain enabled. Set {@code enforceStreamSequence} to
   * false only when a {@link SequenceIntegrityTracker} will classify every parsed message.
   */
  public StpParserClientResult parse(
      String host,
      int port,
      int maximumFrameSize,
      boolean enforceStreamSequence,
      Consumer<StpParseEvent> eventConsumer)
      throws IOException {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("host must not be blank");
    }
    if (port < 1 || port > 65_535) {
      throw new IllegalArgumentException("port must be between 1 and 65535");
    }
    Objects.requireNonNull(eventConsumer, "eventConsumer must not be null");

    IncrementalStpDecoder decoder =
        new IncrementalStpDecoder(maximumFrameSize, enforceStreamSequence);
    EventCounter counter = new EventCounter(eventConsumer);
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port));
      try (InputStream input = socket.getInputStream()) {
        byte[] readBuffer = new byte[READ_BUFFER_SIZE];
        int read;
        while ((read = input.read(readBuffer)) != -1) {
          counter.acceptAll(decoder.feed(ByteBuffer.wrap(readBuffer, 0, read)));
        }
      }
    }
    counter.acceptAll(decoder.endOfInput());
    return new StpParserClientResult(counter.parsedFrames, counter.parseFailures);
  }

  private static final class EventCounter {
    private final Consumer<StpParseEvent> eventConsumer;
    private long parsedFrames;
    private long parseFailures;

    private EventCounter(Consumer<StpParseEvent> eventConsumer) {
      this.eventConsumer = eventConsumer;
    }

    private void acceptAll(List<StpParseEvent> events) {
      for (StpParseEvent event : events) {
        if (event instanceof ParsedStpFrame) {
          parsedFrames++;
        } else {
          parseFailures++;
        }
        eventConsumer.accept(event);
      }
    }
  }
}
