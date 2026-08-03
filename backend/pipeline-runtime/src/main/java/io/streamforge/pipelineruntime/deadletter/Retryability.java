package io.streamforge.pipelineruntime.deadletter;

/** Classifies whether retrying the same record could succeed without changing its contents. */
public enum Retryability {
  RETRYABLE,
  NON_RETRYABLE
}
