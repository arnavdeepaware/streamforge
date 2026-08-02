package io.streamforge.stp.protocol;

/** Raised when a wire code does not identify a known STP v1 message type. */
public final class UnknownMessageTypeException extends StpValidationException {

  UnknownMessageTypeException(char wireCode) {
    super("unknown STP v1 message type: " + wireCode);
  }
}
