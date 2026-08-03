package io.streamforge.pipelineruntime.deadletter;

/** Selects how the local runtime responds after recording a record-level failure. */
public enum DeadLetterPolicy {
  FAIL_FAST,
  SKIP,
  QUARANTINE
}
