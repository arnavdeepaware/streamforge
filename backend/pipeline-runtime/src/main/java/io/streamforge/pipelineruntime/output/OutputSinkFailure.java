package io.streamforge.pipelineruntime.output;

/** Structured reason a pipeline output sink could not complete its lifecycle. */
public record OutputSinkFailure(OutputSinkFailureStage stage, String detail) {
  public OutputSinkFailure {
    if (stage == null || detail == null || detail.isBlank()) {
      throw new IllegalArgumentException("output sink failure requires a stage and detail");
    }
  }
}
