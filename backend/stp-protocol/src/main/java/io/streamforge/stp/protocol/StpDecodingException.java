package io.streamforge.stp.protocol;

/** Base exception for malformed or incomplete STP input. */
public class StpDecodingException extends StpProtocolException {

  public StpDecodingException(String message) {
    super(message);
  }

  public StpDecodingException(String message, Throwable cause) {
    super(message, cause);
  }
}
