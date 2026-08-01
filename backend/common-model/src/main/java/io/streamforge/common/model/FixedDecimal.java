package io.streamforge.common.model;

import java.math.BigDecimal;

/**
 * An exact decimal value represented by a signed mantissa and a decimal scale.
 *
 * <p>The supported scale range matches STP v1. Convert to {@link BigDecimal} only at a display or
 * serialization boundary.
 */
public record FixedDecimal(long mantissa, int scale) {

  public static final int MAX_SCALE = 18;

  public FixedDecimal {
    if (scale < 0 || scale > MAX_SCALE) {
      throw new IllegalArgumentException("scale must be between 0 and " + MAX_SCALE);
    }
  }

  /** Returns this exact value for an explicit display or serialization boundary. */
  public BigDecimal toBigDecimal() {
    return BigDecimal.valueOf(mantissa, scale);
  }

  @Override
  public String toString() {
    return toBigDecimal().toPlainString();
  }
}
