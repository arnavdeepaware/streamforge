package io.streamforge.common.model;

/** Canonical representation of a quantity cancelled from a known order. */
public record OrderCancelled(OrderId orderId, Quantity cancelledQuantity) implements MarketEvent {

  public OrderCancelled {
    if (orderId == null || cancelledQuantity == null) {
      throw new IllegalArgumentException("order-cancelled fields must not be null");
    }
  }

  @Override
  public CanonicalEventType type() {
    return CanonicalEventType.ORDER_CANCELLED;
  }
}
