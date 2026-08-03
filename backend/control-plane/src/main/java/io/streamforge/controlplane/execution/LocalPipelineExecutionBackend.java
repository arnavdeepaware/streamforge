package io.streamforge.controlplane.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.streamforge.pipelineruntime.LocalPipelineRunner;
import io.streamforge.pipelineruntime.PipelineCancellation;
import io.streamforge.pipelineruntime.PipelineConfigLoader;
import io.streamforge.pipelineruntime.PipelineRunObserver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Executes one revision locally; its interface intentionally permits a future remote backend. */
@Component
public final class LocalPipelineExecutionBackend
    implements PipelineExecutionBackend, AutoCloseable {
  private final ObjectMapper objectMapper;
  private final Path workspace;
  private final Path inputRoot;
  private final Path artifactRoot;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public LocalPipelineExecutionBackend(
      ObjectMapper objectMapper,
      @Value("${streamforge.local-pipeline.workspace:.}") String workspace,
      @Value("${streamforge.local-pipeline.input-root:.}") String inputRoot,
      @Value("${streamforge.local-pipeline.artifact-root:.streamforge/artifacts}")
          String artifactRoot) {
    this.objectMapper = objectMapper;
    this.workspace = Path.of(workspace).toAbsolutePath().normalize();
    this.inputRoot = Path.of(inputRoot).toAbsolutePath().normalize();
    this.artifactRoot = Path.of(artifactRoot).toAbsolutePath().normalize();
  }

  @Override
  public PipelineExecutionHandle start(
      PipelineExecutionCommand command, PipelineExecutionListener listener) {
    PipelineCancellation cancellation = new PipelineCancellation();
    executor.submit(() -> execute(command, listener, cancellation));
    return cancellation::cancel;
  }

  private void execute(
      PipelineExecutionCommand command,
      PipelineExecutionListener listener,
      PipelineCancellation cancellation) {
    try {
      listener.onRunning();
    } catch (RuntimeException failure) {
      listener.onFailed(failure);
      return;
    }
    PipelineExecutionResult result;
    try {
      MaterializedExecution materialized = materialize(command);
      Path config = materialized.config();
      try {
        var runConfig = new PipelineConfigLoader().load(config);
        PipelineRunObserver observer =
            new PipelineRunObserver() {
              @Override
              public void onMetrics(io.streamforge.pipelineruntime.PipelineRunMetrics metrics) {
                listener.onMetrics(metrics);
              }

              @Override
              public void onDeadLetter(
                  io.streamforge.pipelineruntime.deadletter.DeadLetterRecord record) {
                listener.onDeadLetter(record);
              }
            };
        var report = new LocalPipelineRunner(observer).run(runConfig, cancellation);
        Optional<String> outputArtifact =
            Files.isRegularFile(materialized.outputFile())
                ? Optional.of(materialized.outputArtifactPath())
                : Optional.empty();
        Optional<String> deadLetterArtifact =
            materialized.deadLetterFile().filter(Files::isRegularFile).isPresent()
                ? materialized.deadLetterArtifactPath()
                : Optional.empty();
        result = new PipelineExecutionResult(report, outputArtifact, deadLetterArtifact);
      } finally {
        Files.deleteIfExists(config);
        Files.deleteIfExists(config.resolveSibling(config.getFileName() + ".transform.json"));
        Files.deleteIfExists(config.resolveSibling(config.getFileName() + ".blueprint.json"));
      }
    } catch (Throwable failure) {
      listener.onFailed(failure);
      return;
    }
    listener.onCompleted(result);
  }

  private MaterializedExecution materialize(PipelineExecutionCommand command) throws IOException {
    Files.createDirectories(workspace);
    Path resolvedInput = resolveInput(command.input());
    Files.createDirectories(artifactRoot);
    Path outputRelative =
        Path.of(command.runId().toString())
            .resolve("output")
            .resolve(relativePath(command.output(), "path"));
    Path outputFile = resolveManagedArtifact(outputRelative);
    Optional<Path> deadLetterRelative =
        command
            .deadLetter()
            .filter(
                option ->
                    option.policy()
                        == io.streamforge.pipelineruntime.deadletter.DeadLetterPolicy.QUARANTINE)
            .map(
                ignored -> Path.of(command.runId().toString(), "dead-letter", "dead-letter.jsonl"));
    Optional<Path> deadLetterFile =
        deadLetterRelative.map(
            relative -> {
              try {
                return resolveManagedArtifact(relative);
              } catch (IOException exception) {
                throw new ManagedPathFailure(exception);
              }
            });
    Path config = Files.createTempFile(workspace, "streamforge-run-", ".json");
    ObjectNode root = objectMapper.createObjectNode();
    root.put("schemaVersion", "1.0");
    root.put("pipelineId", command.pipelineId().toString());
    root.put("pipelineVersion", Long.toString(command.revisionNumber()));
    ObjectNode input = command.input().deepCopy();
    input.put("path", resolvedInput.toString());
    root.set("input", input);
    ObjectNode output = command.output().deepCopy();
    output.put("path", outputFile.toString());
    root.set("output", output);
    command
        .deadLetter()
        .ifPresent(
            options -> {
              ObjectNode deadLetter = root.putObject("deadLetter");
              deadLetter.put("policy", options.policy().name());
              deadLetter.put("includePayload", options.includePayload());
              deadLetter.put("maximumPayloadBytes", options.maximumPayloadBytes());
              deadLetterFile.ifPresent(path -> deadLetter.put("path", path.toString()));
            });
    if (command.transform() != null && !command.transform().isBlank()) {
      Path transform = config.resolveSibling(config.getFileName() + ".transform.json");
      Files.writeString(transform, command.transform());
      root.put("transformation", transform.toString());
    }
    if (command.blueprint() != null && !command.blueprint().isBlank()) {
      Path blueprint = config.resolveSibling(config.getFileName() + ".blueprint.json");
      Files.writeString(blueprint, command.blueprint());
      root.put("blueprint", blueprint.toString());
    }
    Files.writeString(config, objectMapper.writeValueAsString(root));
    return new MaterializedExecution(
        config,
        outputFile,
        portable(outputRelative),
        deadLetterFile,
        deadLetterRelative.map(LocalPipelineExecutionBackend::portable));
  }

  private Path resolveInput(com.fasterxml.jackson.databind.JsonNode input) throws IOException {
    Path relative = relativePath(input, "path");
    Path root = inputRoot.toRealPath();
    Path resolved = root.resolve(relative).normalize().toRealPath();
    if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
      throw new IOException("input path must identify a regular file beneath the configured root");
    }
    return resolved;
  }

  private Path resolveManagedArtifact(Path relative) throws IOException {
    Path root = artifactRoot.toRealPath();
    Path resolved = root.resolve(relative).normalize();
    if (!resolved.startsWith(root)) {
      throw new IOException("artifact path escapes the configured root");
    }
    Path parent = resolved.getParent();
    if (parent == null) throw new IOException("artifact path requires a parent directory");
    Files.createDirectories(parent);
    if (!parent.toRealPath().startsWith(root)) {
      throw new IOException("artifact parent escapes the configured root");
    }
    return resolved;
  }

  private static Path relativePath(com.fasterxml.jackson.databind.JsonNode node, String field)
      throws IOException {
    if (node == null
        || !node.isObject()
        || !node.hasNonNull(field)
        || !node.get(field).isTextual()) {
      throw new IOException(field + " must be a relative path");
    }
    try {
      Path path = Path.of(node.get(field).asText());
      Path normalized = path.normalize();
      if (path.isAbsolute() || normalized.toString().isBlank() || normalized.startsWith("..")) {
        throw new IOException(field + " must stay beneath the configured root");
      }
      return normalized;
    } catch (InvalidPathException exception) {
      throw new IOException(field + " is not a valid path", exception);
    }
  }

  private static String portable(Path path) {
    return path.toString().replace(path.getFileSystem().getSeparator(), "/");
  }

  @Override
  public void close() {
    executor.close();
  }

  private record MaterializedExecution(
      Path config,
      Path outputFile,
      String outputArtifactPath,
      Optional<Path> deadLetterFile,
      Optional<String> deadLetterArtifactPath) {}

  private static final class ManagedPathFailure extends RuntimeException {
    private ManagedPathFailure(IOException cause) {
      super(cause);
    }
  }
}
