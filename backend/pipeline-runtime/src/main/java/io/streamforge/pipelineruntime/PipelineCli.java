package io.streamforge.pipelineruntime;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

/** Command-line entry point for one saved local pipeline configuration. */
public final class PipelineCli {
  private PipelineCli() {}

  public static void main(String[] arguments) {
    int exitCode = run(arguments, System.out, System.err);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  /** Runs the CLI without terminating the caller, which keeps it testable. */
  public static int run(String[] arguments, PrintStream output, PrintStream errors) {
    if (arguments == null || output == null || errors == null) {
      throw new IllegalArgumentException("arguments and streams must not be null");
    }
    if (arguments.length == 1 && "--help".equals(arguments[0])) {
      usage(output);
      return 0;
    }
    if (arguments.length != 2 || !"--config".equals(arguments[0])) {
      usage(errors);
      return 2;
    }
    try {
      PipelineRunConfig config = new PipelineConfigLoader().load(Path.of(arguments[1]));
      PipelineReport report = new LocalPipelineRunner().run(config, new PipelineCancellation());
      output.println(
          "received="
              + report.counters().received()
              + " parsed="
              + report.counters().parsed()
              + " normalized="
              + report.counters().normalized()
              + " filtered="
              + report.counters().filtered()
              + " emitted="
              + report.counters().emitted()
              + " failed="
              + report.counters().failed()
              + " cancelled="
              + report.cancelled());
      for (PipelineFailure failure : report.failures()) {
        errors.println(failure.stage() + " " + failure.sourceLocation() + ": " + failure.detail());
      }
      if (report.suppressedFailureCount() > 0) {
        errors.println("suppressed failures: " + report.suppressedFailureCount());
      }
      return report.counters().failed() == 0 && !report.cancelled() ? 0 : 1;
    } catch (PipelineConfigurationException | IOException exception) {
      String location =
          exception instanceof PipelineConfigurationException configuration
              ? configuration.location()
              : arguments[1];
      errors.println("configuration error at " + location + ": " + exception.getMessage());
      return 2;
    }
  }

  private static void usage(PrintStream stream) {
    stream.println("Usage: PipelineCli --config <pipeline-config.json>");
  }
}
