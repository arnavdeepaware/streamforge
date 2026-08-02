package io.streamforge.stp.protocol;

/** Raised when a validated STP message cannot be encoded. */
public final class StpEncodingException extends StpProtocolException {

  StpEncodingException(String message) {
    super(message);
  }
}
