package io.streamforge.parserengine;

/** Identifies one logical source or session whose STP sequences are independently tracked. */
public record SequenceSource(String value) {

  public SequenceSource {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("sequence source must not be blank");
    }
    value = value.trim();
  }
}
