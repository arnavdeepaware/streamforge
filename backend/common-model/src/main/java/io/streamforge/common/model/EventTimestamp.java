package io.streamforge.common.model;

/**
 * A Unix-epoch timestamp stored as an exact count of nanoseconds.
 *
 * <p>STP encodes timestamps as uint64 values. Java has no unsigned {@code long} value type, so the
 * supported domain is {@code 0..Long.MAX_VALUE}.
 */
public record EventTimestamp(long nanosecondsSinceEpoch) {

  public EventTimestamp {
    if (nanosecondsSinceEpoch < 0) {
      throw new IllegalArgumentException("nanosecondsSinceEpoch must not be negative");
    }
  }

  @Override
  public String toString() {
    return nanosecondsSinceEpoch + "ns since Unix epoch";
  }
}
