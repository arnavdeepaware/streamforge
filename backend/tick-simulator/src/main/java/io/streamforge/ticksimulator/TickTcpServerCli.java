package io.streamforge.ticksimulator;

import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.SequenceNumber;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/** Command-line entry point for serving deterministic STP frames over TCP. */
public final class TickTcpServerCli {

  private TickTcpServerCli() {}

  public static void main(String[] arguments) {
    int exitCode = run(arguments, System.err);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  /** Runs the server CLI and returns a POSIX-style exit code without terminating the JVM. */
  public static int run(String[] arguments, PrintStream standardError) {
    try {
      ServerOptions options = ServerOptions.parse(arguments);
      if (options.helpRequested) {
        standardError.print(usage());
        return 0;
      }
      TickTcpServer server = new TickTcpServer(options.toConfig(), standardError::println);
      Thread shutdownHook =
          Thread.ofPlatform().name("streamforge-tick-shutdown").unstarted(server::close);
      Runtime.getRuntime().addShutdownHook(shutdownHook);
      try {
        server.start();
        server.awaitFirstConnectionCompletion();
        return 0;
      } finally {
        server.close();
        removeShutdownHook(shutdownHook);
      }
    } catch (UsageException error) {
      standardError.println("error: " + error.getMessage());
      standardError.println();
      standardError.print(usage());
      return 2;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      standardError.println("error: server interrupted");
      return 1;
    } catch (Exception error) {
      standardError.println("error: unable to serve STP frames: " + error.getMessage());
      return 1;
    }
  }

  private static String usage() {
    return """
        Usage: TickTcpServerCli [options]
          --host <hostname>             Bind address (default: 127.0.0.1)
          --port <0..65535>             TCP port; 0 selects an ephemeral port (default: 9010)
          --seed <long>                 Random seed (default: 1)
          --symbols <AAPL,MSFT>         Comma-separated STP symbols (default: AAPL,MSFT)
          --count <non-negative long>   Events for the next client (default: 100)
          --continuous                  Stream until the client disconnects or the server stops
          --rate <events-per-second>    0 is unthrottled (default: 0)
          --help                        Show this message
        """;
  }

  private static void removeShutdownHook(Thread shutdownHook) {
    try {
      Runtime.getRuntime().removeShutdownHook(shutdownHook);
    } catch (IllegalStateException ignored) {
      // The JVM is already shutting down and will run the hook itself.
    }
  }

  private static final class ServerOptions {
    private String host = "127.0.0.1";
    private int port = 9_010;
    private long seed = 1;
    private List<InstrumentSymbol> symbols = defaultSymbols();
    private long count = 100;
    private boolean countProvided;
    private boolean continuous;
    private long rate;
    private boolean helpRequested;

    private static ServerOptions parse(String[] arguments) {
      if (arguments == null) {
        throw new UsageException("arguments must not be null");
      }
      ServerOptions options = new ServerOptions();
      for (int index = 0; index < arguments.length; index++) {
        String option = arguments[index];
        if ("--help".equals(option)) {
          options.helpRequested = true;
          continue;
        }
        if ("--continuous".equals(option)) {
          options.continuous = true;
          continue;
        }
        String value = requireValue(arguments, ++index, option);
        switch (option) {
          case "--host" -> options.host = value;
          case "--port" -> options.port = parsePort(value);
          case "--seed" -> options.seed = parseLong(value, option);
          case "--symbols" -> options.symbols = parseSymbols(value);
          case "--count" -> {
            options.count = parseNonNegativeLong(value, option);
            options.countProvided = true;
          }
          case "--rate" -> options.rate = parseNonNegativeLong(value, option);
          default -> throw new UsageException("unknown option " + option);
        }
      }
      if (options.continuous && options.countProvided) {
        throw new UsageException("--continuous cannot be combined with --count");
      }
      if (options.rate > 1_000_000_000L) {
        throw new UsageException("--rate must not exceed 1000000000");
      }
      return options;
    }

    private TickTcpServerConfig toConfig() {
      TickSimulationConfig simulation =
          new TickSimulationConfig(
              seed,
              symbols,
              continuous ? ContinuousSimulation.INSTANCE : new FiniteSimulation(count),
              EventTypeDistribution.defaults(),
              new SequenceNumber(1),
              new EventTimestamp(0),
              1_000_000,
              1_000);
      return new TickTcpServerConfig(host, port, simulation, rate);
    }

    private static List<InstrumentSymbol> defaultSymbols() {
      return List.of(new InstrumentSymbol("AAPL"), new InstrumentSymbol("MSFT"));
    }

    private static String requireValue(String[] arguments, int index, String option) {
      if (index >= arguments.length || arguments[index].startsWith("--")) {
        throw new UsageException(option + " requires a value");
      }
      return arguments[index];
    }

    private static int parsePort(String value) {
      long port = parseNonNegativeLong(value, "--port");
      if (port > 65_535) {
        throw new UsageException("--port must be between 0 and 65535");
      }
      return (int) port;
    }

    private static List<InstrumentSymbol> parseSymbols(String value) {
      if (value.isBlank()) {
        throw new UsageException("--symbols must not be empty");
      }
      List<InstrumentSymbol> symbols = new ArrayList<>();
      try {
        for (String symbol : value.split(",", -1)) {
          symbols.add(new InstrumentSymbol(symbol));
        }
      } catch (IllegalArgumentException error) {
        throw new UsageException("invalid --symbols value: " + error.getMessage());
      }
      return List.copyOf(symbols);
    }

    private static long parseLong(String value, String option) {
      try {
        return Long.parseLong(value);
      } catch (NumberFormatException error) {
        throw new UsageException(option + " must be a Java long");
      }
    }

    private static long parseNonNegativeLong(String value, String option) {
      long parsed = parseLong(value, option);
      if (parsed < 0) {
        throw new UsageException(option + " must not be negative");
      }
      return parsed;
    }
  }

  private static final class UsageException extends IllegalArgumentException {
    private UsageException(String message) {
      super(message);
    }
  }
}
