package io.streamforge.stp.protocol;

/** Raised when a known STP message does not use its specified frame length. */
public final class InvalidFrameLengthException extends StpValidationException {

  InvalidFrameLengthException(int actualLength, MessageType messageType) {
    super(message(actualLength, messageType));
  }

  private static String message(int actualLength, MessageType messageType) {
    if (messageType == null) {
      return "encoded length must be between "
          + StpProtocol.MIN_ENCODED_LENGTH
          + " and "
          + StpProtocol.MAX_ENCODED_LENGTH
          + ": "
          + actualLength;
    }
    return "encoded length for "
        + messageType
        + " must be "
        + messageType.encodedLength()
        + ": "
        + actualLength;
  }
}
