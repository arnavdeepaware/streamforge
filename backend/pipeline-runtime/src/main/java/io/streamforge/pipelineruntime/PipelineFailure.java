package io.streamforge.pipelineruntime;

/** One bounded, source-located diagnostic retained in a final pipeline report. */
public record PipelineFailure(PipelineStage stage, String sourceLocation, String detail) {
  public PipelineFailure {
    if (stage == null
        || sourceLocation == null
        || sourceLocation.isBlank()
        || detail == null
        || detail.isBlank()) {
      throw new IllegalArgumentException(
          "pipeline failure requires stage, source location, and detail");
    }
  }
}
