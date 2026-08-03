package io.streamforge.common.model;

/** Canonical representation of a quantity executed against a known order. */
public record OrderExecuted(OrderId orderId, Quantity executedQuantity) implements MarketEvent {

  public OrderExecuted {
    if (orderId == null || executedQuantity == null) {
      throw new IllegalArgumentException("order-executed fields must not be null");
    }
  }

  @Override
  public CanonicalEventType type() {
    return CanonicalEventType.ORDER_EXECUTED;
  }
}
