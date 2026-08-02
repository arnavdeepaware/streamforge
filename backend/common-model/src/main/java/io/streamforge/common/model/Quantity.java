package io.streamforge.common.model;

/**
 * A positive quantity compatible with an unsigned 32-bit wire value.
 *
 * <p>Java has no unsigned 32-bit value type, so the full STP domain is represented in a signed
 * {@code long}.
 */
public record Quantity(long value) {

  public static final long MAX_VALUE = 4_294_967_295L;

  public Quantity {
    if (value < 1 || value > MAX_VALUE) {
      throw new IllegalArgumentException("quantity must be between 1 and " + MAX_VALUE);
    }
  }

  @Override
  public String toString() {
    return Long.toString(value);
  }
}
