package io.streamforge.controlplane.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.streamforge.pipelineruntime.PipelineOutcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPipelineExecutionBackendTest {
  @TempDir Path temporaryDirectory;
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void publishesOnlyRunRelativeArtifactsAfterSuccessfulSinkCompletion() throws Exception {
    Path inputRoot = Files.createDirectories(temporaryDirectory.resolve("input"));
    Path artifactRoot = Files.createDirectories(temporaryDirectory.resolve("artifacts"));
    Path workspace = Files.createDirectories(temporaryDirectory.resolve("workspace"));
    Files.copy(example("pipeline-aapl-input.jsonl"), inputRoot.resolve("sample.jsonl"));
    UUID runId = UUID.randomUUID();

    Callback callback = new Callback();
    try (LocalPipelineExecutionBackend backend =
        new LocalPipelineExecutionBackend(
            mapper, workspace.toString(), inputRoot.toString(), artifactRoot.toString())) {
      backend.start(command(runId, "sample.jsonl", "result.jsonl"), callback);
      callback.await();
    }

    assertThat(callback.failure.get()).isNull();
    PipelineExecutionResult result = callback.result.get();
    assertThat(result.report().outcome()).isEqualTo(PipelineOutcome.COMPLETED);
    assertThat(result.outputArtifactPath()).contains(runId + "/output/result.jsonl");
    assertThat(result.outputArtifactPath().orElseThrow()).doesNotStartWith("/");
    assertThat(artifactRoot.resolve(result.outputArtifactPath().orElseThrow())).isRegularFile();
  }

  @Test
  void rejectsAbsoluteTraversalAndInputSymlinkEscapes() throws Exception {
    Path inputRoot = Files.createDirectories(temporaryDirectory.resolve("input"));
    Path artifactRoot = Files.createDirectories(temporaryDirectory.resolve("artifacts"));
    Path workspace = Files.createDirectories(temporaryDirectory.resolve("workspace"));
    Path outside = Files.createDirectories(temporaryDirectory.resolve("outside"));
    Files.writeString(outside.resolve("sample.jsonl"), "{}\n");
    Files.createSymbolicLink(inputRoot.resolve("escape"), outside);

    assertRejected(
        workspace,
        inputRoot,
        artifactRoot,
        outside.resolve("sample.jsonl").toString(),
        "out.jsonl");
    assertRejected(workspace, inputRoot, artifactRoot, "../outside/sample.jsonl", "out.jsonl");
    assertRejected(workspace, inputRoot, artifactRoot, "escape/sample.jsonl", "out.jsonl");
  }

  @Test
  void rejectsOutputPathsAndArtifactSymlinksThatEscapeTheManagedRoot() throws Exception {
    Path inputRoot = Files.createDirectories(temporaryDirectory.resolve("input"));
    Path artifactRoot = Files.createDirectories(temporaryDirectory.resolve("artifacts"));
    Path workspace = Files.createDirectories(temporaryDirectory.resolve("workspace"));
    Files.copy(example("pipeline-aapl-input.jsonl"), inputRoot.resolve("sample.jsonl"));

    assertRejected(workspace, inputRoot, artifactRoot, "sample.jsonl", "/tmp/out.jsonl");
    assertRejected(workspace, inputRoot, artifactRoot, "sample.jsonl", "../out.jsonl");

    UUID runId = UUID.randomUUID();
    Path outputParent =
        Files.createDirectories(artifactRoot.resolve(runId.toString()).resolve("output"));
    Path outside = Files.createDirectories(temporaryDirectory.resolve("outside-artifacts"));
    Files.createSymbolicLink(outputParent.resolve("escape"), outside);
    Callback callback = new Callback();
    try (LocalPipelineExecutionBackend backend =
        new LocalPipelineExecutionBackend(
            mapper, workspace.toString(), inputRoot.toString(), artifactRoot.toString())) {
      backend.start(command(runId, "sample.jsonl", "escape/out.jsonl"), callback);
      callback.await();
    }
    assertThat(callback.failure.get()).isNotNull();
    assertThat(Files.exists(outside.resolve("out.jsonl"))).isFalse();
  }

  private void assertRejected(
      Path workspace, Path inputRoot, Path artifactRoot, String input, String output)
      throws Exception {
    Callback callback = new Callback();
    try (LocalPipelineExecutionBackend backend =
        new LocalPipelineExecutionBackend(
            mapper, workspace.toString(), inputRoot.toString(), artifactRoot.toString())) {
      backend.start(command(UUID.randomUUID(), input, output), callback);
      callback.await();
    }
    assertThat(callback.result.get()).isNull();
    assertThat(callback.failure.get()).isNotNull();
  }

  private PipelineExecutionCommand command(UUID runId, String inputPath, String outputPath) {
    ObjectNode input = mapper.createObjectNode();
    input.put("type", "JSONL");
    input.put("path", inputPath);
    input.put("mode", "CONTINUE_WITH_ERRORS");
    ObjectNode output = mapper.createObjectNode();
    output.put("type", "JSONL");
    output.put("path", outputPath);
    return new PipelineExecutionCommand(
        runId, UUID.randomUUID(), 1, input, null, null, output, Optional.empty());
  }

  private static Path example(String name) {
    Path direct = Path.of("schemas/examples", name);
    return Files.exists(direct) ? direct : Path.of("../../schemas/examples", name);
  }

  private static final class Callback implements PipelineExecutionListener {
    private final CountDownLatch terminal = new CountDownLatch(1);
    private final AtomicReference<PipelineExecutionResult> result = new AtomicReference<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    @Override
    public void onRunning() {}

    @Override
    public void onCompleted(PipelineExecutionResult result) {
      this.result.set(result);
      terminal.countDown();
    }

    @Override
    public void onFailed(Throwable failure) {
      this.failure.set(failure);
      terminal.countDown();
    }

    private void await() throws InterruptedException {
      assertThat(terminal.await(5, TimeUnit.SECONDS)).isTrue();
    }
  }
}
