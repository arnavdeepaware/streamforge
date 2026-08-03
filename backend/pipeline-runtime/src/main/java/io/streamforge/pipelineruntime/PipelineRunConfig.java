package io.streamforge.pipelineruntime;

import io.streamforge.pipelineruntime.deadletter.DeadLetterConfig;
import java.nio.file.Path;
import java.util.Optional;

/** Activated local-pipeline settings, including optional transform and output blueprint files. */
public record PipelineRunConfig(
    PipelineInput input,
    Optional<Path> transformationConfig,
    Optional<Path> blueprintConfig,
    PipelineOutput output,
    PipelineIdentity identity,
    Optional<DeadLetterConfig> deadLetterConfig) {
  public PipelineRunConfig(
      PipelineInput input,
      Optional<Path> transformationConfig,
      Optional<Path> blueprintConfig,
      PipelineOutput output) {
    this(
        input,
        transformationConfig,
        blueprintConfig,
        output,
        PipelineIdentity.localDefault(),
        Optional.empty());
  }

  public PipelineRunConfig {
    if (input == null
        || transformationConfig == null
        || blueprintConfig == null
        || output == null
        || identity == null
        || deadLetterConfig == null) {
      throw new IllegalArgumentException("pipeline run configuration fields must not be null");
    }
  }
}
