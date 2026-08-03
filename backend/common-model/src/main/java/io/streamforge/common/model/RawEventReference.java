package io.streamforge.common.model;

/** Opaque stable reference to the captured raw record from which an event was normalized. */
public record RawEventReference(String value) {

  public static final int MAX_LENGTH = 256;

  public RawEventReference {
    if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "raw event reference must contain between 1 and 256 characters");
    }
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character < 0x21 || character > 0x7E) {
        throw new IllegalArgumentException(
            "raw event reference must use printable, non-space ASCII characters");
      }
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
