package io.streamforge.stp.protocol;

import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.Quantity;
import io.streamforge.common.model.Side;
import io.streamforge.common.model.TradeId;

/** A fixed-length STP v1 Trade message. */
public record TradeMessage(
    FrameHeader header,
    TradeId tradeId,
    InstrumentSymbol symbol,
    Side aggressorSide,
    Quantity quantity,
    FixedDecimal price)
    implements StpMessage {

  public TradeMessage {
    header = StpMessageValidator.requireHeaderType(header, MessageType.TRADE);
    tradeId = StpMessageValidator.require(tradeId, "tradeId");
    symbol = StpMessageValidator.require(symbol, "symbol");
    aggressorSide = StpMessageValidator.require(aggressorSide, "aggressorSide");
    quantity = StpMessageValidator.require(quantity, "quantity");
    price = StpMessageValidator.require(price, "price");
  }
}
