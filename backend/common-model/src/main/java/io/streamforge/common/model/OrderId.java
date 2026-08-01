package io.streamforge.common.model;

/**
 * A nonnegative source order identifier.
 *
 * <p>STP encodes order IDs as uint64 values. Java stores the accepted {@code 0..Long.MAX_VALUE}
 * domain in a signed {@code long}.
 */
public record OrderId(long value) {

  public OrderId {
    if (value < 0) {
      throw new IllegalArgumentException("order ID must not be negative");
    }
  }

  @Override
  public String toString() {
    return Long.toString(value);
  }
}
