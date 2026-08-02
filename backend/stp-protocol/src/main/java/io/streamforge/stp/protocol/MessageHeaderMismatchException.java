package io.streamforge.stp.protocol;

/** Raised when a message record is supplied with a header for another message type. */
public final class MessageHeaderMismatchException extends StpValidationException {

  MessageHeaderMismatchException(MessageType expected, MessageType actual) {
    super("message header type must be " + expected + ": " + actual);
  }
}
