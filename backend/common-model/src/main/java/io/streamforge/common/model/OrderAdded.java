package io.streamforge.common.model;

/** Canonical representation of an order being added to a market. */
public record OrderAdded(OrderId orderId, Side side, Quantity quantity, FixedDecimal price)
    implements MarketEvent {

  public OrderAdded {
    if (orderId == null || side == null || quantity == null || price == null) {
      throw new IllegalArgumentException("order-added fields must not be null");
    }
  }

  @Override
  public CanonicalEventType type() {
    return CanonicalEventType.ORDER_ADDED;
  }
}
