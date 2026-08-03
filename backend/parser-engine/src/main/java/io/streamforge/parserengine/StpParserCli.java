package io.streamforge.parserengine;

import io.streamforge.stp.protocol.ParsedStpFrame;
import io.streamforge.stp.protocol.StpParseEvent;
import io.streamforge.stp.protocol.StpParseFailure;
import java.io.PrintStream;

/** Command-line client that prints parsed STP TCP events and reports parse failures. */
public final class StpParserCli {

  private StpParserCli() {}

  public static void main(String[] arguments) {
    int exitCode = run(arguments, System.out, System.err);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  /** Runs the parser CLI and returns a POSIX-style exit code without terminating the JVM. */
  public static int run(String[] arguments, PrintStream standardOutput, PrintStream standardError) {
    try {
      ClientOptions options = ClientOptions.parse(arguments);
      if (options.helpRequested) {
        standardOutput.print(usage());
        return 0;
      }
      StpParserClientResult result =
          new StpTcpParserClient()
              .parse(
                  options.host,
                  options.port,
                  event -> printEvent(event, standardOutput, standardError));
      standardError.println(
          "connection complete: "
              + result.parsedFrames()
              + " parsed frames, "
              + result.parseFailures()
              + " parse failures");
      return result.parseFailures() == 0 ? 0 : 1;
    } catch (UsageException error) {
      standardError.println("error: " + error.getMessage());
      standardError.println();
      standardError.print(usage());
      return 2;
    } catch (Exception error) {
      standardError.println("connection error: " + error.getMessage());
      return 1;
    }
  }

  private static void printEvent(
      StpParseEvent event, PrintStream standardOutput, PrintStream standardError) {
    switch (event) {
      case ParsedStpFrame frame -> standardOutput.println("parsed: " + frame.result());
      case StpParseFailure failure ->
          standardError.println(
              "parse error (recoverable=" + failure.recoverable() + "): " + failure.error());
    }
  }

  private static String usage() {
    return """
        Usage: StpParserCli [options]
          --host <hostname>             Server address (default: 127.0.0.1)
          --port <1..65535>             Server TCP port (default: 9010)
          --help                        Show this message
        """;
  }

  private static final class ClientOptions {
    private String host = "127.0.0.1";
    private int port = 9_010;
    private boolean helpRequested;

    private static ClientOptions parse(String[] arguments) {
      if (arguments == null) {
        throw new UsageException("arguments must not be null");
      }
      ClientOptions options = new ClientOptions();
      for (int index = 0; index < arguments.length; index++) {
        String option = arguments[index];
        if ("--help".equals(option)) {
          options.helpRequested = true;
          continue;
        }
        String value = requireValue(arguments, ++index, option);
        switch (option) {
          case "--host" -> options.host = value;
          case "--port" -> options.port = parsePort(value);
          default -> throw new UsageException("unknown option " + option);
        }
      }
      return options;
    }

    private static String requireValue(String[] arguments, int index, String option) {
      if (index >= arguments.length || arguments[index].startsWith("--")) {
        throw new UsageException(option + " requires a value");
      }
      return arguments[index];
    }

    private static int parsePort(String value) {
      try {
        int port = Integer.parseInt(value);
        if (port < 1 || port > 65_535) {
          throw new UsageException("--port must be between 1 and 65535");
        }
        return port;
      } catch (NumberFormatException error) {
        throw new UsageException("--port must be an integer");
      }
    }
  }

  private static final class UsageException extends IllegalArgumentException {
    private UsageException(String message) {
      super(message);
    }
  }
}
