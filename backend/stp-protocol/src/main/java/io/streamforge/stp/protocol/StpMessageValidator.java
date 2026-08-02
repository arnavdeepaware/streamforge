package io.streamforge.stp.protocol;

final class StpMessageValidator {

  private StpMessageValidator() {}

  static FrameHeader requireHeaderType(FrameHeader header, MessageType expectedType) {
    require(header, "header");
    if (header.messageType() != expectedType) {
      throw new MessageHeaderMismatchException(expectedType, header.messageType());
    }
    return header;
  }

  static <T> T require(T value, String name) {
    if (value == null) {
      throw new StpValidationException(name + " must not be null");
    }
    return value;
  }
}
