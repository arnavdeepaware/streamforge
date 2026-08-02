package io.streamforge.stp.protocol;

import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.Quantity;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Deterministically encodes validated STP v1 messages in network byte order. */
public final class StpEncoder {

  public byte[] encode(StpMessage message) {
    if (message == null) {
      throw new StpEncodingException("message must not be null");
    }

    ByteBuffer target = ByteBuffer.allocate(message.totalFrameSize()).order(ByteOrder.BIG_ENDIAN);
    writeHeader(target, message.header());
    switch (message) {
      case AddOrderMessage addOrder -> writeAddOrder(target, addOrder);
      case ExecuteOrderMessage executeOrder -> writeExecuteOrder(target, executeOrder);
      case CancelOrderMessage cancelOrder -> writeCancelOrder(target, cancelOrder);
      case TradeMessage trade -> writeTrade(target, trade);
    }
    if (target.hasRemaining()) {
      throw new StpEncodingException(
          "message layout left " + target.remaining() + " frame bytes unwritten");
    }
    return target.array();
  }

  private static void writeHeader(ByteBuffer target, FrameHeader header) {
    target.putShort((short) header.encodedLength());
    target.put((byte) header.messageType().wireCode());
    target.putLong(header.sequenceNumber().value());
    target.putLong(header.eventTimestamp().nanosecondsSinceEpoch());
  }

  private static void writeAddOrder(ByteBuffer target, AddOrderMessage message) {
    target.putLong(message.orderId().value());
    writeSymbol(target, message.symbol());
    target.put((byte) message.side().stpCode());
    writeQuantity(target, message.quantity());
    writePrice(target, message.price());
  }

  private static void writeExecuteOrder(ByteBuffer target, ExecuteOrderMessage message) {
    target.putLong(message.orderId().value());
    writeQuantity(target, message.executedQuantity());
  }

  private static void writeCancelOrder(ByteBuffer target, CancelOrderMessage message) {
    target.putLong(message.orderId().value());
    writeQuantity(target, message.canceledQuantity());
  }

  private static void writeTrade(ByteBuffer target, TradeMessage message) {
    target.putLong(message.tradeId().value());
    writeSymbol(target, message.symbol());
    target.put((byte) message.aggressorSide().stpCode());
    writeQuantity(target, message.quantity());
    writePrice(target, message.price());
  }

  private static void writeSymbol(ByteBuffer target, InstrumentSymbol symbol) {
    String paddedSymbol = symbol.toStpPaddedAscii();
    for (int index = 0; index < paddedSymbol.length(); index++) {
      target.put((byte) paddedSymbol.charAt(index));
    }
  }

  private static void writeQuantity(ByteBuffer target, Quantity quantity) {
    target.putInt((int) quantity.value());
  }

  private static void writePrice(ByteBuffer target, FixedDecimal price) {
    target.putLong(price.mantissa());
    target.put((byte) price.scale());
  }
}
