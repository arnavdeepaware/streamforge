package io.streamforge.transform.compile;

/** Stable categories for semantic transformation compilation failures. */
public enum ValidationErrorCode {
  UNKNOWN_FIELD,
  TARGET_EXISTS,
  INVALID_TARGET,
  PROTECTED_FIELD,
  TYPE_MISMATCH,
  UNSUPPORTED_CAST,
  INVALID_COMPARISON,
  DUPLICATE_FIELD
}
