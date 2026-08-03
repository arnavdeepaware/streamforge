package io.streamforge.pipelineruntime.deadletter;

/** A bounded set of local dead-letter failure categories. */
public enum DeadLetterCategory {
  MALFORMED_INPUT,
  VALIDATION,
  NORMALIZATION,
  TRANSFORMATION,
  BLUEPRINT,
  OUTPUT,
  INPUT_IO
}
