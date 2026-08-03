package io.streamforge.common.model;

/** One exact price and positive displayed quantity in a canonical quote. */
public record QuoteLevel(FixedDecimal price, Quantity quantity) {

  public QuoteLevel {
    if (price == null || quantity == null) {
      throw new IllegalArgumentException("quote-level fields must not be null");
    }
  }
}
