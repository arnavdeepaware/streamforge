package io.streamforge.transform.config;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Validated dotted field path that cannot encode method or class access. */
public record FieldPath(String value) implements Comparable<FieldPath> {

  private static final int MAX_LENGTH = 256;
  private static final int MAX_SEGMENTS = 16;
  private static final Pattern SEGMENT = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
  private static final Set<String> FORBIDDEN_SEGMENTS =
      Set.of(
          "class", "classLoader", "getClass", "metaClass", "prototype", "constructor", "__proto__");

  public FieldPath {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("field path must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "field path must not exceed " + MAX_LENGTH + " characters");
    }
    String[] segments = value.split("\\.", -1);
    if (segments.length > MAX_SEGMENTS) {
      throw new IllegalArgumentException(
          "field path must not exceed " + MAX_SEGMENTS + " segments");
    }
    for (String segment : segments) {
      if (!SEGMENT.matcher(segment).matches()) {
        throw new IllegalArgumentException("invalid field path segment: " + segment);
      }
      if (FORBIDDEN_SEGMENTS.contains(segment)) {
        throw new IllegalArgumentException("forbidden field path segment: " + segment);
      }
    }
  }

  /** Returns the immutable path segments. */
  public List<String> segments() {
    return List.of(value.split("\\."));
  }

  /** Returns the parent path, or {@code null} for a root field. */
  public FieldPath parent() {
    int separator = value.lastIndexOf('.');
    return separator < 0 ? null : new FieldPath(value.substring(0, separator));
  }

  /** Returns whether this path is the same as, or nested below, the supplied path. */
  public boolean isSameOrDescendantOf(FieldPath other) {
    return value.equals(other.value) || value.startsWith(other.value + ".");
  }

  /** Replaces an ancestor prefix while preserving any nested suffix. */
  public FieldPath replacePrefix(FieldPath oldPrefix, FieldPath newPrefix) {
    if (!isSameOrDescendantOf(oldPrefix)) {
      throw new IllegalArgumentException("path is not under the prefix being replaced");
    }
    return value.equals(oldPrefix.value)
        ? newPrefix
        : new FieldPath(newPrefix.value + value.substring(oldPrefix.value.length()));
  }

  @Override
  public int compareTo(FieldPath other) {
    return value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return value;
  }
}
