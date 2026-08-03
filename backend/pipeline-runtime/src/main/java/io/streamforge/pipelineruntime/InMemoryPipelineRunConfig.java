package io.streamforge.pipelineruntime;

import io.streamforge.pipelineruntime.deadletter.DeadLetterConfig;
import java.util.Optional;

/** Immutable local execution snapshot whose transform and blueprint are already in memory. */
public record InMemoryPipelineRunConfig(
    PipelineInput input,
    Optional<String> transformationConfiguration,
    Optional<String> blueprintConfiguration,
    PipelineOutput output,
    PipelineIdentity identity,
    Optional<DeadLetterConfig> deadLetterConfig) {
  public InMemoryPipelineRunConfig {
    if (input == null
        || transformationConfiguration == null
        || blueprintConfiguration == null
        || output == null
        || identity == null
        || deadLetterConfig == null) {
      throw new IllegalArgumentException(
          "in-memory pipeline configuration fields must not be null");
    }
  }
}
