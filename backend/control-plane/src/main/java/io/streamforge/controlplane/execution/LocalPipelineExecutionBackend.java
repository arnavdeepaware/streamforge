package io.streamforge.controlplane.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.streamforge.pipelineruntime.LocalPipelineRunner;
import io.streamforge.pipelineruntime.PipelineCancellation;
import io.streamforge.pipelineruntime.PipelineConfigLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public LocalPipelineExecutionBackend(
      ObjectMapper objectMapper,
      @Value("${streamforge.local-pipeline.workspace:.}") String workspace) {
    this.objectMapper = objectMapper;
    this.workspace = Path.of(workspace).toAbsolutePath();
  }

  @Override
  public PipelineExecutionHandle start(
      PipelineExecutionCommand command, PipelineExecutionListener listener) {
    PipelineCancellation cancellation = new PipelineCancellation();
    executor.submit(
        () -> {
          try {
            listener.onRunning();
            Path config = materialize(command);
            try {
              var runConfig = new PipelineConfigLoader().load(config);
              listener.onCompleted(new LocalPipelineRunner().run(runConfig, cancellation));
            } finally {
              Files.deleteIfExists(config);
              Files.deleteIfExists(config.resolveSibling(config.getFileName() + ".transform.json"));
              Files.deleteIfExists(config.resolveSibling(config.getFileName() + ".blueprint.json"));
            }
          } catch (Throwable failure) {
            listener.onFailed(failure);
          }
        });
    return cancellation::cancel;
  }

  private Path materialize(PipelineExecutionCommand command) throws IOException {
    Files.createDirectories(workspace);
    Path config = Files.createTempFile(workspace, "streamforge-run-", ".json");
    ObjectNode root = objectMapper.createObjectNode();
    root.put("schemaVersion", "1.0");
    root.put("pipelineId", command.pipelineId().toString());
    root.put("pipelineVersion", Long.toString(command.revisionNumber()));
    root.set("input", command.input());
    root.set("output", command.output());
    if (command.deadLetter() != null && !command.deadLetter().isNull())
      root.set("deadLetter", command.deadLetter());
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
    return config;
  }

  @Override
  public void close() {
    executor.close();
  }
}
