package io.streamforge.common.model;

import java.util.Locale;

/**
 * A normalized source venue identifier.
 *
 * <p>The value is uppercase ASCII and deliberately does not claim a particular venue taxonomy. A
 * later normalization layer can map it to a MIC or another standard identifier.
 */
public record Venue(String value) {

  public static final int MAX_LENGTH = 32;

  public Venue {
    if (value == null) {
      throw new IllegalArgumentException("venue must not be null");
    }
    if (value.isEmpty() || value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("venue must contain between 1 and 32 characters");
    }
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (!isAsciiIdentifierCharacter(character)) {
        throw new IllegalArgumentException(
            "venue must use ASCII letters, digits, hyphen, underscore, or period");
      }
    }
    value = value.toUpperCase(Locale.ROOT);
  }

  @Override
  public String toString() {
    return value;
  }

  private static boolean isAsciiIdentifierCharacter(char character) {
    return (character >= 'A' && character <= 'Z')
        || (character >= 'a' && character <= 'z')
        || (character >= '0' && character <= '9')
        || character == '-'
        || character == '_'
        || character == '.';
  }
}
