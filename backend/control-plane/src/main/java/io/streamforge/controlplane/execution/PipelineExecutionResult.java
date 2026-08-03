package io.streamforge.controlplane.execution;

import io.streamforge.pipelineruntime.PipelineReport;
import java.util.Optional;

/** Final report and managed artifacts produced by one local execution. */
public record PipelineExecutionResult(
    PipelineReport report,
    Optional<String> outputArtifactPath,
    Optional<String> deadLetterArtifactPath) {
  public PipelineExecutionResult {
    if (report == null || outputArtifactPath == null || deadLetterArtifactPath == null) {
      throw new IllegalArgumentException("execution result fields must not be null");
    }
  }
}
