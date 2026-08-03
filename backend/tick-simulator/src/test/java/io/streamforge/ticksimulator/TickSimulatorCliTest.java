package io.streamforge.ticksimulator;

import static org.assertj.core.api.Assertions.assertThat;

import io.streamforge.stp.protocol.IncrementalStpDecoder;
import io.streamforge.stp.protocol.ParsedStpFrame;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TickSimulatorCliTest {

  @TempDir Path temporaryDirectory;

  @Test
  void writesAFiniteBinaryFixtureThatTheStpParserAccepts() throws Exception {
    Path outputFile = temporaryDirectory.resolve("ticks.stp");
    ByteArrayOutputStream errors = new ByteArrayOutputStream();

    int exitCode =
        TickSimulatorCli.run(
            new String[] {
              "--seed",
              "17",
              "--symbols",
              "AAPL,MSFT",
              "--count",
              "25",
              "--output",
              outputFile.toString()
            },
            new ByteArrayOutputStream(),
            new PrintStream(errors));

    byte[] fixture = Files.readAllBytes(outputFile);
    assertThat(exitCode).isZero();
    assertThat(errors.toByteArray()).isEmpty();
    assertThat(new IncrementalStpDecoder(49).feed(fixture))
        .hasSize(25)
        .allMatch(ParsedStpFrame.class::isInstance);
  }

  @Test
  void stdoutIsDeterministicForTheSameArguments() {
    byte[] first = runToStandardOutput();
    byte[] second = runToStandardOutput();

    assertThat(first).isEqualTo(second);
  }

  @Test
  void invalidOptionsHaveUsefulMessagesAndAUsageExitCode() {
    ByteArrayOutputStream errors = new ByteArrayOutputStream();
    int exitCode =
        TickSimulatorCli.run(
            new String[] {"--count", "-1"}, new ByteArrayOutputStream(), new PrintStream(errors));

    assertThat(exitCode).isEqualTo(2);
    assertThat(errors.toString())
        .contains("error:")
        .contains("--count must not be negative")
        .contains("Usage:");
  }

  private static byte[] runToStandardOutput() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode =
        TickSimulatorCli.run(
            new String[] {"--seed", "22", "--count", "10", "--output", "-"},
            output,
            new PrintStream(new ByteArrayOutputStream()));
    assertThat(exitCode).isZero();
    return output.toByteArray();
  }
}
