package io.streamforge.transform.execute;

/** Expected data-dependent document access failure used to form a typed execution result. */
final class DocumentAccessException extends Exception {

  private final TransformationFailureCode code;
  private final String fieldPath;

  DocumentAccessException(TransformationFailureCode code, String fieldPath, String message) {
    super(message);
    this.code = code;
    this.fieldPath = fieldPath;
  }

  TransformationFailureCode code() {
    return code;
  }

  String fieldPath() {
    return fieldPath;
  }
}
