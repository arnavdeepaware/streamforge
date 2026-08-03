package io.streamforge.transform.blueprint;

/** Stable categories for raw output blueprint parse failures. */
public enum BlueprintConfigErrorCode {
  MALFORMED_JSON,
  UNKNOWN_PROPERTY,
  MISSING_PROPERTY,
  INVALID_VALUE,
  UNKNOWN_KIND,
  UNKNOWN_CONDITION,
  UNSUPPORTED_VERSION,
  LIMIT_EXCEEDED
}
