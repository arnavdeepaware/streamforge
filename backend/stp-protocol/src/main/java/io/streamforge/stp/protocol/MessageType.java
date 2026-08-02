package io.streamforge.stp.protocol;

/** The known STP v1 message types and their fixed encoded lengths. */
public enum MessageType {
  ADD_ORDER('A', StpProtocol.ADD_ORDER_ENCODED_LENGTH),
  EXECUTE_ORDER('E', StpProtocol.EXECUTE_ORDER_ENCODED_LENGTH),
  CANCEL_ORDER('C', StpProtocol.CANCEL_ORDER_ENCODED_LENGTH),
  TRADE('T', StpProtocol.TRADE_ENCODED_LENGTH);

  private final char wireCode;
  private final int encodedLength;

  MessageType(char wireCode, int encodedLength) {
    this.wireCode = wireCode;
    this.encodedLength = encodedLength;
  }

  /** Returns the ASCII byte value represented as a character. */
  public char wireCode() {
    return wireCode;
  }

  /** Returns the bytes after the length field for this fixed v1 message. */
  public int encodedLength() {
    return encodedLength;
  }

  /** Resolves a known STP v1 wire code. */
  public static MessageType fromWireCode(char wireCode) {
    return switch (wireCode) {
      case 'A' -> ADD_ORDER;
      case 'E' -> EXECUTE_ORDER;
      case 'C' -> CANCEL_ORDER;
      case 'T' -> TRADE;
      default -> throw new UnknownMessageTypeException(wireCode);
    };
  }
}
