package io.streamforge.stp.protocol;

/** Base exception for STP model values that violate the v1 specification. */
public class StpValidationException extends IllegalArgumentException {

  public StpValidationException(String message) {
    super(message);
  }
}
