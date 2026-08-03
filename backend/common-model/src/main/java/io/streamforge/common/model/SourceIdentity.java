package io.streamforge.common.model;

/**
 * Stable identity of one source stream or session.
 *
 * <p>A source that resets its sequence numbers must use a new identity so deterministic event IDs
 * remain unique.
 */
public record SourceIdentity(String value) {

  public static final int MAX_LENGTH = 128;

  public SourceIdentity {
    if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "source identity must contain between 1 and 128 characters");
    }
    if (!isInitialCharacter(value.charAt(0))) {
      throw new IllegalArgumentException(
          "source identity must begin with an ASCII letter or digit");
    }
    for (int index = 1; index < value.length(); index++) {
      if (!isIdentifierCharacter(value.charAt(index))) {
        throw new IllegalArgumentException(
            "source identity contains an unsupported ASCII character");
      }
    }
  }

  @Override
  public String toString() {
    return value;
  }

  private static boolean isInitialCharacter(char character) {
    return (character >= 'A' && character <= 'Z')
        || (character >= 'a' && character <= 'z')
        || (character >= '0' && character <= '9');
  }

  private static boolean isIdentifierCharacter(char character) {
    return isInitialCharacter(character)
        || character == '.'
        || character == '_'
        || character == ':'
        || character == '/'
        || character == '@'
        || character == '-';
  }
}
