package io.streamforge.common.model;

/** Identifies a major and minor version of the canonical event contract. */
public record CanonicalSchemaVersion(int major, int minor) {

  public static final CanonicalSchemaVersion V1_0 = new CanonicalSchemaVersion(1, 0);

  public CanonicalSchemaVersion {
    if (major < 1) {
      throw new IllegalArgumentException("schema major version must be positive");
    }
    if (minor < 0) {
      throw new IllegalArgumentException("schema minor version must not be negative");
    }
  }

  @Override
  public String toString() {
    return major + "." + minor;
  }
}
