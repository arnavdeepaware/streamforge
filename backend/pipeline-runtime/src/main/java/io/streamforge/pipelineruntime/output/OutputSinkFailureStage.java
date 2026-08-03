package io.streamforge.pipelineruntime.output;

/** Lifecycle stage associated with one typed output failure. */
public enum OutputSinkFailureStage {
  START,
  WRITE,
  FLUSH,
  COMPLETE,
  LIFECYCLE
}
