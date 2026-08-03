package io.streamforge.transform.execute;

/**
 * Structured failure for one event and one operation without terminating later event processing.
 */
public record TransformationFailure(
    TransformationFailureCode code,
    int operationIndex,
    String operationName,
    String fieldPath,
    String detail) {

  public TransformationFailure {
    if (code == null
        || operationIndex < 0
        || operationName == null
        || operationName.isBlank()
        || fieldPath == null
        || fieldPath.isBlank()
        || detail == null
        || detail.isBlank()) {
      throw new IllegalArgumentException("transformation failure fields must be present");
    }
  }
}
