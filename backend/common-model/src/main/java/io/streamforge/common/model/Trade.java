package io.streamforge.common.model;

import java.util.Optional;

/** Canonical representation of a market trade. */
public record Trade(
    TradeId tradeId, Optional<Side> aggressorSide, Quantity quantity, FixedDecimal price)
    implements MarketEvent {

  public Trade {
    if (tradeId == null || aggressorSide == null || quantity == null || price == null) {
      throw new IllegalArgumentException("trade fields must not be null");
    }
  }

  @Override
  public CanonicalEventType type() {
    return CanonicalEventType.TRADE;
  }
}
