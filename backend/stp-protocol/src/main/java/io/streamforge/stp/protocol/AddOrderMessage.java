package io.streamforge.stp.protocol;

import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.Quantity;
import io.streamforge.common.model.Side;

/** A fixed-length STP v1 Add Order message. */
public record AddOrderMessage(
    FrameHeader header,
    OrderId orderId,
    InstrumentSymbol symbol,
    Side side,
    Quantity quantity,
    FixedDecimal price)
    implements StpMessage {

  public AddOrderMessage {
    header = StpMessageValidator.requireHeaderType(header, MessageType.ADD_ORDER);
    orderId = StpMessageValidator.require(orderId, "orderId");
    symbol = StpMessageValidator.require(symbol, "symbol");
    side = StpMessageValidator.require(side, "side");
    quantity = StpMessageValidator.require(quantity, "quantity");
    price = StpMessageValidator.require(price, "price");
  }
}
