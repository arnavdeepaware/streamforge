package io.streamforge.pipelineruntime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.streamforge.parserengine.JsonLinesMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RawCaptureTest {
  @TempDir Path temporaryDirectory;

  @Test
  void preservesExactBytesAndWritesAChecksummedManifest() throws Exception {
    byte[] bytes = new byte[] {0, 1, 2, 10, 13, -1};
    Path source = temporaryDirectory.resolve("ticks\nraw.bin");
    Files.write(source, bytes);
    UUID runId = UUID.fromString("ef03b03f-378a-4f6d-b095-d9215ef47f48");
    PipelineRunArtifacts artifacts =
        new PipelineRunArtifacts(runId, temporaryDirectory.resolve("artifacts"));

    PipelineInput captured =
        RawCapture.capture(
            new PipelineInput.JsonLines(source, JsonLinesMode.CONTINUE_WITH_ERRORS),
            artifacts,
            Clock.fixed(Instant.parse("2026-09-15T04:00:00Z"), ZoneOffset.UTC));

    assertThat(captured.path()).isEqualTo(artifacts.rawCapture());
    assertThat(Files.readAllBytes(artifacts.rawCapture())).containsExactly(bytes);
    JsonNode manifest = new ObjectMapper().readTree(artifacts.rawCaptureManifest().toFile());
    assertThat(manifest.path("runId").textValue()).isEqualTo(runId.toString());
    assertThat(manifest.path("inputType").textValue()).isEqualTo("JSONL");
    assertThat(manifest.path("originalFileName").textValue()).isEqualTo("ticks\nraw.bin");
    assertThat(manifest.path("byteLength").longValue()).isEqualTo(bytes.length);
    assertThat(manifest.path("sha256").textValue())
        .isEqualTo(
            HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
    assertThat(manifest.path("capturedAt").textValue()).isEqualTo("2026-09-15T04:00:00Z");
    assertThat(manifest.path("storedFileName").textValue()).isEqualTo("raw-input.capture");
  }

  @Test
  void refusesToOverwriteAnExistingRunCapture() throws Exception {
    Path source = temporaryDirectory.resolve("input.jsonl");
    Files.writeString(source, "first");
    PipelineRunArtifacts artifacts =
        new PipelineRunArtifacts(UUID.randomUUID(), temporaryDirectory.resolve("artifacts"));
    RawCapture.capture(
        new PipelineInput.JsonLines(source, JsonLinesMode.CONTINUE_WITH_ERRORS),
        artifacts,
        Clock.systemUTC());
    Files.writeString(source, "second");

    assertThatThrownBy(
            () ->
                RawCapture.capture(
                    new PipelineInput.JsonLines(source, JsonLinesMode.CONTINUE_WITH_ERRORS),
                    artifacts,
                    Clock.systemUTC()))
        .isInstanceOf(java.io.IOException.class)
        .hasMessageContaining("already exist");
    assertThat(Files.readString(artifacts.rawCapture())).isEqualTo("first");
  }

  @Test
  void capturedCopyIsIsolatedFromLaterSourceMutation() throws Exception {
    Path source = temporaryDirectory.resolve("source.jsonl");
    Files.copy(example("pipeline-aapl-input.jsonl"), source);
    PipelineRunArtifacts artifacts = artifacts("isolated");
    PipelineInput captured =
        RawCapture.capture(
            new PipelineInput.JsonLines(source, JsonLinesMode.CONTINUE_WITH_ERRORS),
            artifacts,
            Clock.systemUTC());
    Files.writeString(source, "{}\n");
    Path output = temporaryDirectory.resolve("isolated-output.jsonl");

    PipelineReport report =
        new LocalPipelineRunner()
            .run(
                new PipelineRunConfig(
                    captured,
                    Optional.empty(),
                    Optional.empty(),
                    new PipelineOutput.JsonLines(output)),
                new PipelineCancellation());

    assertThat(report.outcome()).isEqualTo(PipelineOutcome.COMPLETED);
    assertThat(report.counters().emitted()).isEqualTo(1);
    assertThat(Files.readString(output)).contains("\"symbol\":\"AAPL\"");
  }

  @Test
  void retainsCapturesForCancelledAndFailedRuns() throws Exception {
    Path input = example("pipeline-aapl-input.jsonl");

    PipelineCancellation cancelled = new PipelineCancellation();
    cancelled.cancel();
    PipelineRunArtifacts cancelledArtifacts = artifacts("cancelled");
    PipelineReport cancelledReport =
        new LocalPipelineRunner()
            .run(
                config(input, temporaryDirectory.resolve("cancelled.jsonl")),
                cancelled,
                cancelledArtifacts);
    assertThat(cancelledReport.outcome()).isEqualTo(PipelineOutcome.CANCELLED);
    assertThat(cancelledArtifacts.rawCapture()).exists();
    assertThat(cancelledArtifacts.rawCaptureManifest()).exists();

    Path occupied = temporaryDirectory.resolve("occupied");
    Files.writeString(occupied, "not a directory");
    PipelineRunArtifacts failedArtifacts = artifacts("failed");
    PipelineReport failedReport =
        new LocalPipelineRunner()
            .run(
                config(input, occupied.resolve("output.jsonl")),
                new PipelineCancellation(),
                failedArtifacts);
    assertThat(failedReport.outcome()).isEqualTo(PipelineOutcome.FAILED);
    assertThat(failedArtifacts.rawCapture()).exists();
    assertThat(failedArtifacts.rawCaptureManifest()).exists();
  }

  private PipelineRunArtifacts artifacts(String name) {
    return new PipelineRunArtifacts(
        UUID.randomUUID(), temporaryDirectory.resolve("artifacts").resolve(name));
  }

  private static PipelineRunConfig config(Path input, Path output) {
    return new PipelineRunConfig(
        new PipelineInput.JsonLines(input, JsonLinesMode.CONTINUE_WITH_ERRORS),
        Optional.empty(),
        Optional.empty(),
        new PipelineOutput.JsonLines(output));
  }

  private static Path example(String name) {
    Path direct = Path.of("schemas/examples", name);
    return Files.exists(direct) ? direct : Path.of("../../schemas/examples", name);
  }
}
