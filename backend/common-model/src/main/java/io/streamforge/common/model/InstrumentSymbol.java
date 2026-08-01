package io.streamforge.common.model;

import java.util.Objects;

/**
 * A normalized instrument symbol compatible with STP's eight-byte ASCII symbol field.
 *
 * <p>Input may include STP trailing-space padding, which is removed. The normalized symbol contains
 * one to eight printable, non-space ASCII characters.
 */
public record InstrumentSymbol(String value) {

  public static final int STP_FIELD_LENGTH = 8;

  public InstrumentSymbol {
    value = normalize(value);
  }

  /** Decodes and normalizes an exact eight-byte STP symbol field. */
  public static InstrumentSymbol fromStpField(byte[] field) {
    Objects.requireNonNull(field, "field must not be null");
    if (field.length != STP_FIELD_LENGTH) {
      throw new IllegalArgumentException("STP symbol field must contain exactly 8 bytes");
    }

    char[] characters = new char[STP_FIELD_LENGTH];
    for (int index = 0; index < field.length; index++) {
      characters[index] = (char) Byte.toUnsignedInt(field[index]);
    }
    return new InstrumentSymbol(new String(characters));
  }

  /** Returns this symbol in STP's fixed-width, space-padded ASCII representation. */
  public String toStpPaddedAscii() {
    return value + " ".repeat(STP_FIELD_LENGTH - value.length());
  }

  @Override
  public String toString() {
    return value;
  }

  private static String normalize(String candidate) {
    if (candidate == null) {
      throw new IllegalArgumentException("symbol must not be null");
    }
    if (candidate.length() > STP_FIELD_LENGTH) {
      throw new IllegalArgumentException("symbol must contain at most 8 bytes");
    }

    boolean paddingStarted = false;
    StringBuilder normalized = new StringBuilder(candidate.length());
    for (int index = 0; index < candidate.length(); index++) {
      char character = candidate.charAt(index);
      if (character == ' ') {
        paddingStarted = true;
        continue;
      }
      if (paddingStarted || character < 0x21 || character > 0x7E) {
        throw new IllegalArgumentException(
            "symbol must use printable ASCII with trailing spaces only");
      }
      normalized.append(character);
    }
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("symbol must not be empty");
    }
    return normalized.toString();
  }
}
