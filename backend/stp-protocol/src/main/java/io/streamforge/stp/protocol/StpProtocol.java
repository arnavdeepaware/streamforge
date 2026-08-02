package io.streamforge.stp.protocol;

/** Constants for the STP v1 frame layouts documented in {@code docs/protocol/stp-v1.md}. */
public final class StpProtocol {

  public static final int LENGTH_FIELD_OFFSET = 0;
  public static final int LENGTH_FIELD_WIDTH = 2;
  public static final int MIN_ENCODED_LENGTH = 1;
  public static final int MAX_ENCODED_LENGTH = 65_535;

  public static final int MESSAGE_TYPE_OFFSET = LENGTH_FIELD_OFFSET + LENGTH_FIELD_WIDTH;
  public static final int MESSAGE_TYPE_WIDTH = 1;
  public static final int SEQUENCE_NUMBER_OFFSET = MESSAGE_TYPE_OFFSET + MESSAGE_TYPE_WIDTH;
  public static final int UINT64_WIDTH = 8;
  public static final int EVENT_TIMESTAMP_OFFSET = SEQUENCE_NUMBER_OFFSET + UINT64_WIDTH;
  public static final int COMMON_HEADER_SIZE = EVENT_TIMESTAMP_OFFSET + UINT64_WIDTH;

  public static final int IDENTIFIER_OFFSET = COMMON_HEADER_SIZE;
  public static final int SYMBOL_OFFSET = IDENTIFIER_OFFSET + UINT64_WIDTH;
  public static final int SYMBOL_WIDTH = 8;
  public static final int SIDE_OFFSET = SYMBOL_OFFSET + SYMBOL_WIDTH;
  public static final int SIDE_WIDTH = 1;
  public static final int QUANTITY_OFFSET = SIDE_OFFSET + SIDE_WIDTH;
  public static final int UINT32_WIDTH = 4;
  public static final int PRICE_MANTISSA_OFFSET = QUANTITY_OFFSET + UINT32_WIDTH;
  public static final int PRICE_SCALE_OFFSET = PRICE_MANTISSA_OFFSET + UINT64_WIDTH;
  public static final int PRICE_SCALE_WIDTH = 1;

  public static final int ADD_ORDER_ENCODED_LENGTH =
      PRICE_SCALE_OFFSET + PRICE_SCALE_WIDTH - LENGTH_FIELD_WIDTH;
  public static final int EXECUTE_ORDER_ENCODED_LENGTH =
      IDENTIFIER_OFFSET + UINT64_WIDTH + UINT32_WIDTH - LENGTH_FIELD_WIDTH;
  public static final int CANCEL_ORDER_ENCODED_LENGTH = EXECUTE_ORDER_ENCODED_LENGTH;
  public static final int TRADE_ENCODED_LENGTH = ADD_ORDER_ENCODED_LENGTH;

  private StpProtocol() {}

  /** Returns the complete frame size, including the two-byte length field. */
  public static int totalFrameSize(int encodedLength) {
    if (encodedLength < MIN_ENCODED_LENGTH || encodedLength > MAX_ENCODED_LENGTH) {
      throw new InvalidFrameLengthException(encodedLength, null);
    }
    return LENGTH_FIELD_WIDTH + encodedLength;
  }
}
