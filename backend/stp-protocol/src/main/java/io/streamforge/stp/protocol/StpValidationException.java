package io.streamforge.stp.protocol;

/** Base exception for STP model values that violate the v1 specification. */
public class StpValidationException extends StpProtocolException {

  public StpValidationException(String message) {
    super(message);
  }
}
