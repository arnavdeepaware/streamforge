package io.streamforge.common.model;

/** Typed payload carried by a {@link CanonicalEvent}. */
public sealed interface MarketEvent
    permits OrderAdded, OrderExecuted, OrderCancelled, Trade, Quote {

  /** Returns the stable discriminator for this payload. */
  CanonicalEventType type();
}
