package io.streamforge.common.model;

/**
 * A nonnegative source trade identifier.
 *
 * <p>STP encodes trade IDs as uint64 values. Java stores the accepted {@code 0..Long.MAX_VALUE}
 * domain in a signed {@code long}.
 */
public record TradeId(long value) {

  public TradeId {
    if (value < 0) {
      throw new IllegalArgumentException("trade ID must not be negative");
    }
  }

  @Override
  public String toString() {
    return Long.toString(value);
  }
}
