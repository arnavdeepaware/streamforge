package io.streamforge.common.model;

import java.util.Optional;

/**
 * Canonical top-of-book quote for adapters that provide quoted markets.
 *
 * <p>Each side is optional, but at least one side must be present. Absence is therefore distinct
 * from a quantity, whose value is always positive.
 */
public record Quote(Optional<QuoteLevel> bid, Optional<QuoteLevel> ask) implements MarketEvent {

  public Quote {
    if (bid == null || ask == null) {
      throw new IllegalArgumentException("quote sides must not be null");
    }
    if (bid.isEmpty() && ask.isEmpty()) {
      throw new IllegalArgumentException("quote must contain at least one side");
    }
  }

  @Override
  public CanonicalEventType type() {
    return CanonicalEventType.QUOTE;
  }
}
