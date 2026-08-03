package io.streamforge.parserengine;

/** Stable classification for an STP-to-canonical normalization failure. */
public enum StpNormalizationFailureReason {
  INVALID_INPUT,
  INVALID_CONTEXT,
  UNSUPPORTED_MESSAGE_TYPE,
  INSTRUMENT_NOT_RESOLVED,
  NORMALIZATION_ERROR
}
