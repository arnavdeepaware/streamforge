package io.streamforge.pipelineruntime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineCliTest {
  @TempDir Path temporaryDirectory;

  @Test
  void printsUsageWithoutTerminatingTheCaller() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    int exitCode = PipelineCli.run(new String[] {"--help"}, new PrintStream(output), System.err);

    assertThat(exitCode).isZero();
    assertThat(output.toString()).contains("Usage: PipelineCli");
  }

  @Test
  void runsASavedConfigurationAndPrintsTheFinalReport() throws Exception {
    Path input = example("pipeline-aapl-input.jsonl").toAbsolutePath();
    Path destination = temporaryDirectory.resolve("result.jsonl");
    Path config = temporaryDirectory.resolve("pipeline.json");
    Files.writeString(
        config,
        """
        {"schemaVersion":"1.0","input":{"type":"JSONL","path":"%s"},"output":{"type":"JSONL","path":"%s"}}
        """
            .formatted(input, destination));
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    int exitCode =
        PipelineCli.run(
            new String[] {"--config", config.toString()}, new PrintStream(output), System.err);

    assertThat(exitCode).isZero();
    assertThat(output.toString()).contains("received=1").contains("emitted=1");
    assertThat(Files.readString(destination)).contains("\"symbol\":\"AAPL\"");
  }

  private static Path example(String name) {
    Path direct = Path.of("schemas/examples", name);
    return Files.exists(direct) ? direct : Path.of("../../schemas/examples", name);
  }
}
