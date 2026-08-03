package io.streamforge.transform.blueprint;

/** Stable semantic validation failures for an output blueprint. */
public enum BlueprintValidationCode {
  UNKNOWN_FIELD,
  TRANSFORMED_SCHEMA_REQUIRED,
  TYPE_MISMATCH,
  UNSUPPORTED_FORMAT,
  LIMIT_EXCEEDED
}
