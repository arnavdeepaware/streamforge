package io.streamforge.pipelineruntime;

/** Final execution outcome for one finite local pipeline run. */
public enum PipelineOutcome {
  COMPLETED,
  CANCELLED,
  FAILED
}
