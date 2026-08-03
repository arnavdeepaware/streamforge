package io.streamforge.pipelineruntime;

/** Stable local-pipeline stage associated with an event failure. */
public enum PipelineStage {
  INPUT,
  PARSE,
  NORMALIZE,
  TRANSFORM,
  BLUEPRINT,
  OUTPUT,
  CONFIGURATION
}
