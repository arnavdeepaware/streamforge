package io.streamforge.transform.config;

/** Stable categories for transformation configuration parse failures. */
public enum ConfigurationErrorCode {
  MALFORMED_JSON,
  UNKNOWN_PROPERTY,
  MISSING_PROPERTY,
  INVALID_VALUE,
  UNSUPPORTED_VERSION,
  UNKNOWN_OPERATION,
  UNKNOWN_CONDITION,
  LIMIT_EXCEEDED
}
