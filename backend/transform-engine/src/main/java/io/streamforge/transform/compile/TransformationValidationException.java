package io.streamforge.transform.compile;

/** Typed semantic failure tied to the operation that could not be compiled. */
public final class TransformationValidationException extends Exception {

  private final ValidationErrorCode code;
  private final int operationIndex;

  public TransformationValidationException(
      ValidationErrorCode code, int operationIndex, String message) {
    super(message);
    if (code == null || operationIndex < 0) {
      throw new IllegalArgumentException("validation error code and operation index are required");
    }
    this.code = code;
    this.operationIndex = operationIndex;
  }

  public ValidationErrorCode code() {
    return code;
  }

  public int operationIndex() {
    return operationIndex;
  }
}
