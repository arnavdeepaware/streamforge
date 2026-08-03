package io.streamforge.transform.execute;

/** Stable categories for a data-dependent transformation execution failure. */
public enum TransformationFailureCode {
  OPERATION_LIMIT_EXCEEDED,
  NESTING_DEPTH_EXCEEDED,
  OUTPUT_FIELD_LIMIT_EXCEEDED,
  MISSING_FIELD,
  TYPE_MISMATCH,
  INVALID_CAST,
  PRECISION_LOSS,
  UNMAPPED_ENUM_VALUE,
  INVALID_DOCUMENT,
  UNEXPECTED_RUNTIME_FAILURE
}
