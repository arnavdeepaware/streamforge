package io.streamforge.parserengine;

/** Stable classification for one JSON Lines input failure. */
public enum JsonLinesErrorReason {
  EMPTY_LINE,
  MALFORMED_JSON,
  REQUIRED_FIELD,
  INVALID_FIELD,
  SCHEMA_VERSION,
  EVENT_ID_MISMATCH,
  UNSUPPORTED_EVENT_TYPE,
  NORMALIZATION_ERROR
}
