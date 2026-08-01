package io.streamforge.common.model;

/**
 * A positive source sequence number used to preserve event order within a stream.
 *
 * <p>STP encodes sequences as uint64 values. Java stores the accepted {@code 1..Long.MAX_VALUE}
 * domain in a signed {@code long}.
 */
public record SequenceNumber(long value) {

  public SequenceNumber {
    if (value < 1) {
      throw new IllegalArgumentException("sequence number must be positive");
    }
  }

  @Override
  public String toString() {
    return Long.toString(value);
  }
}
