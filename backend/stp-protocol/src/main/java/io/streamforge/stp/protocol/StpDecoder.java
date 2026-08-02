package io.streamforge.stp.protocol;

import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.Quantity;
import io.streamforge.common.model.SequenceNumber;
import io.streamforge.common.model.Side;
import io.streamforge.common.model.TradeId;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Supplier;

/** Decodes exactly one complete STP v1 frame without retaining or modifying the input buffer. */
public final class StpDecoder {

  public StpDecodeResult decode(byte[] frame) {
    if (frame == null) {
      throw new StpDecodingException("frame must not be null");
    }
    return decode(ByteBuffer.wrap(frame));
  }

  public StpDecodeResult decode(ByteBuffer frame) {
    if (frame == null) {
      throw new StpDecodingException("frame must not be null");
    }

    ByteBuffer source = frame.slice().order(ByteOrder.BIG_ENDIAN);
    int actualBytes = source.remaining();
    if (actualBytes < StpProtocol.LENGTH_FIELD_WIDTH) {
      throw new TruncatedFrameException(StpProtocol.LENGTH_FIELD_WIDTH, actualBytes);
    }

    int encodedLength = Short.toUnsignedInt(source.getShort());
    if (encodedLength < StpProtocol.MIN_ENCODED_LENGTH) {
      throw new InvalidFrameLengthException(encodedLength, null);
    }
    int declaredFrameSize = StpProtocol.totalFrameSize(encodedLength);
    if (actualBytes < declaredFrameSize) {
      throw new TruncatedFrameException(declaredFrameSize, actualBytes);
    }
    if (actualBytes > declaredFrameSize) {
      throw new TrailingFrameBytesException(declaredFrameSize, actualBytes);
    }

    int messageTypeCode = Byte.toUnsignedInt(source.get());
    MessageType messageType;
    try {
      messageType = MessageType.fromWireCode((char) messageTypeCode);
    } catch (UnknownMessageTypeException exception) {
      return new UnknownMessageFrame(encodedLength, messageTypeCode);
    }
    if (encodedLength != messageType.encodedLength()) {
      throw new InvalidFrameLengthException(encodedLength, messageType);
    }

    SequenceNumber sequenceNumber =
        decodeField("sequence number", () -> new SequenceNumber(source.getLong()));
    EventTimestamp eventTimestamp =
        decodeField("event timestamp", () -> new EventTimestamp(source.getLong()));
    FrameHeader header =
        new FrameHeader(encodedLength, messageType, sequenceNumber, eventTimestamp);
    StpMessage message = decodeKnownMessage(source, header);
    if (source.hasRemaining()) {
      throw new StpDecodingException(
          "known message layout left " + source.remaining() + " frame bytes unread");
    }
    return message;
  }

  private static StpMessage decodeKnownMessage(ByteBuffer source, FrameHeader header) {
    return switch (header.messageType()) {
      case ADD_ORDER ->
          new AddOrderMessage(
              header,
              readOrderId(source),
              readSymbol(source),
              readSide(source, "side"),
              readQuantity(source, "quantity"),
              readPrice(source));
      case EXECUTE_ORDER ->
          new ExecuteOrderMessage(
              header, readOrderId(source), readQuantity(source, "executed quantity"));
      case CANCEL_ORDER ->
          new CancelOrderMessage(
              header, readOrderId(source), readQuantity(source, "canceled quantity"));
      case TRADE ->
          new TradeMessage(
              header,
              readTradeId(source),
              readSymbol(source),
              readSide(source, "aggressor side"),
              readQuantity(source, "quantity"),
              readPrice(source));
    };
  }

  private static OrderId readOrderId(ByteBuffer source) {
    return decodeField("order ID", () -> new OrderId(source.getLong()));
  }

  private static TradeId readTradeId(ByteBuffer source) {
    return decodeField("trade ID", () -> new TradeId(source.getLong()));
  }

  private static InstrumentSymbol readSymbol(ByteBuffer source) {
    byte[] symbolBytes = new byte[StpProtocol.SYMBOL_WIDTH];
    source.get(symbolBytes);
    return decodeField("symbol", () -> InstrumentSymbol.fromStpField(symbolBytes));
  }

  private static Side readSide(ByteBuffer source, String fieldName) {
    char code = (char) Byte.toUnsignedInt(source.get());
    return decodeField(fieldName, () -> Side.fromStpCode(code));
  }

  private static Quantity readQuantity(ByteBuffer source, String fieldName) {
    long value = Integer.toUnsignedLong(source.getInt());
    return decodeField(fieldName, () -> new Quantity(value));
  }

  private static FixedDecimal readPrice(ByteBuffer source) {
    long mantissa = source.getLong();
    int scale = Byte.toUnsignedInt(source.get());
    return decodeField("price", () -> new FixedDecimal(mantissa, scale));
  }

  private static <T> T decodeField(String fieldName, Supplier<T> decoder) {
    try {
      return decoder.get();
    } catch (IllegalArgumentException exception) {
      throw new InvalidFieldEncodingException(fieldName, exception);
    }
  }
}
