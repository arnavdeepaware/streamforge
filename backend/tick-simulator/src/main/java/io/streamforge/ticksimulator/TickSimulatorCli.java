package io.streamforge.ticksimulator;

import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.SequenceNumber;
import io.streamforge.stp.protocol.StpEncoder;
import io.streamforge.stp.protocol.StpMessage;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Command-line entry point for generating deterministic binary STP v1 fixtures. */
public final class TickSimulatorCli {

  private static final List<InstrumentSymbol> DEFAULT_SYMBOLS =
      List.of(new InstrumentSymbol("AAPL"), new InstrumentSymbol("MSFT"));

  private TickSimulatorCli() {}

  /** Runs the CLI and returns a POSIX-style process exit code without terminating the JVM. */
  public static int run(
      String[] arguments, OutputStream standardOutput, PrintStream standardError) {
    try {
      CliOptions options = CliOptions.parse(arguments);
      if (options.helpRequested) {
        standardError.print(usage());
        return 0;
      }
      return writeSimulation(options, standardOutput, standardError);
    } catch (UsageException error) {
      standardError.println("error: " + error.getMessage());
      standardError.println();
      standardError.print(usage());
      return 2;
    }
  }

  public static void main(String[] arguments) {
    int exitCode = run(arguments, System.out, System.err);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  private static int writeSimulation(
      CliOptions options, OutputStream standardOutput, PrintStream standardError) {
    try {
      if (options.outputPath == null) {
        writeEvents(options, standardOutput);
      } else {
        try (OutputStream fileOutput =
            new BufferedOutputStream(Files.newOutputStream(options.outputPath))) {
          writeEvents(options, fileOutput);
        }
      }
      return 0;
    } catch (IOException error) {
      standardError.println("error: unable to write STP frames: " + error.getMessage());
      return 1;
    } catch (IllegalStateException error) {
      standardError.println("error: simulation stopped: " + error.getMessage());
      return 1;
    }
  }

  private static void writeEvents(CliOptions options, OutputStream output) throws IOException {
    StpTickEventGenerator generator = new StpTickEventGenerator(options.toConfig());
    StpEncoder encoder = new StpEncoder();
    if (options.continuous) {
      while (!Thread.currentThread().isInterrupted()) {
        output.write(encoder.encode(generator.next().orElseThrow()));
      }
    } else {
      while (true) {
        StpMessage message = generator.next().orElse(null);
        if (message == null) {
          break;
        }
        output.write(encoder.encode(message));
      }
    }
    output.flush();
  }

  private static String usage() {
    return """
        Usage: TickSimulatorCli [options]
          --seed <long>                 Random seed (default: 1)
          --symbols <AAPL,MSFT>         Comma-separated STP symbols (default: AAPL,MSFT)
          --count <non-negative long>   Finite event count (default: 100)
          --continuous                  Generate until interrupted; cannot be combined with --count
          --output <path|->             Binary output path, or - for stdout (default: -)
          --add-weight <integer>        Add Order selection weight (default: 55)
          --execute-weight <integer>    Execute Order selection weight (default: 20)
          --cancel-weight <integer>     Cancel Order selection weight (default: 15)
          --trade-weight <integer>      Trade selection weight (default: 10)
          --max-open-orders <integer>   Maximum retained active orders (default: 1000)
          --start-sequence <long>       Initial positive sequence number (default: 1)
          --start-timestamp-nanos <long> Initial non-negative timestamp (default: 0)
          --timestamp-step-nanos <long> Positive timestamp increment (default: 1000000)
          --help                        Show this message
        """;
  }

  private static final class CliOptions {
    private long seed = 1;
    private List<InstrumentSymbol> symbols = DEFAULT_SYMBOLS;
    private long count = 100;
    private boolean countProvided;
    private boolean continuous;
    private Path outputPath;
    private int addWeight = 55;
    private int executeWeight = 20;
    private int cancelWeight = 15;
    private int tradeWeight = 10;
    private int maximumOpenOrders = 1_000;
    private long startSequence = 1;
    private long startTimestampNanos;
    private long timestampStepNanos = 1_000_000;
    private boolean helpRequested;

    private static CliOptions parse(String[] arguments) {
      if (arguments == null) {
        throw new UsageException("arguments must not be null");
      }
      CliOptions options = new CliOptions();
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
          case "--seed" -> options.seed = parseLong(value, option);
          case "--symbols" -> options.symbols = parseSymbols(value);
          case "--count" -> {
            options.count = parseNonNegativeLong(value, option);
            options.countProvided = true;
          }
          case "--output" -> options.outputPath = "-".equals(value) ? null : Path.of(value);
          case "--add-weight" -> options.addWeight = parseNonNegativeInt(value, option);
          case "--execute-weight" -> options.executeWeight = parseNonNegativeInt(value, option);
          case "--cancel-weight" -> options.cancelWeight = parseNonNegativeInt(value, option);
          case "--trade-weight" -> options.tradeWeight = parseNonNegativeInt(value, option);
          case "--max-open-orders" -> options.maximumOpenOrders = parsePositiveInt(value, option);
          case "--start-sequence" -> options.startSequence = parsePositiveLong(value, option);
          case "--start-timestamp-nanos" ->
              options.startTimestampNanos = parseNonNegativeLong(value, option);
          case "--timestamp-step-nanos" ->
              options.timestampStepNanos = parsePositiveLong(value, option);
          default -> throw new UsageException("unknown option " + option);
        }
      }
      if (options.continuous && options.countProvided) {
        throw new UsageException("--continuous cannot be combined with --count");
      }
      try {
        new EventTypeDistribution(
            options.addWeight, options.executeWeight, options.cancelWeight, options.tradeWeight);
      } catch (IllegalArgumentException | ArithmeticException error) {
        throw new UsageException(error.getMessage());
      }
      return options;
    }

    private TickSimulationConfig toConfig() {
      return new TickSimulationConfig(
          seed,
          symbols,
          continuous ? ContinuousSimulation.INSTANCE : new FiniteSimulation(count),
          new EventTypeDistribution(addWeight, executeWeight, cancelWeight, tradeWeight),
          new SequenceNumber(startSequence),
          new EventTimestamp(startTimestampNanos),
          timestampStepNanos,
          maximumOpenOrders);
    }

    private static String requireValue(String[] arguments, int index, String option) {
      if (index >= arguments.length || arguments[index].startsWith("--")) {
        throw new UsageException(option + " requires a value");
      }
      return arguments[index];
    }

    private static List<InstrumentSymbol> parseSymbols(String value) {
      if (value.isBlank()) {
        throw new UsageException("--symbols must not be empty");
      }
      String[] parts = value.split(",", -1);
      List<InstrumentSymbol> symbols = new ArrayList<>(parts.length);
      try {
        for (String part : parts) {
          symbols.add(new InstrumentSymbol(part));
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

    private static long parsePositiveLong(String value, String option) {
      long parsed = parseLong(value, option);
      if (parsed < 1) {
        throw new UsageException(option + " must be positive");
      }
      return parsed;
    }

    private static int parseNonNegativeInt(String value, String option) {
      long parsed = parseNonNegativeLong(value, option);
      if (parsed > Integer.MAX_VALUE) {
        throw new UsageException(option + " must fit in an integer");
      }
      return (int) parsed;
    }

    private static int parsePositiveInt(String value, String option) {
      int parsed = parseNonNegativeInt(value, option);
      if (parsed < 1) {
        throw new UsageException(option + " must be positive");
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
