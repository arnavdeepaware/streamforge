package io.streamforge.stp.protocol;

/** Base exception for STP protocol or codec failures. */
public class StpProtocolException extends IllegalArgumentException {

  public StpProtocolException(String message) {
    super(message);
  }

  public StpProtocolException(String message, Throwable cause) {
    super(message, cause);
  }
}
