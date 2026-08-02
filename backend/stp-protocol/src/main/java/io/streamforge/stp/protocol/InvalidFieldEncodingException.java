package io.streamforge.stp.protocol;

/** Raised when a complete known frame contains a field value forbidden by STP v1. */
public final class InvalidFieldEncodingException extends StpDecodingException {

  private final String fieldName;

  InvalidFieldEncodingException(String fieldName, IllegalArgumentException cause) {
    super("invalid STP " + fieldName + ": " + cause.getMessage(), cause);
    this.fieldName = fieldName;
  }

  public String fieldName() {
    return fieldName;
  }
}
