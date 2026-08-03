package io.streamforge.transform.config;

/** Supported transformation configuration schema versions. */
public enum TransformationSchemaVersion {
  V1_0("1.0");

  private final String externalValue;

  TransformationSchemaVersion(String externalValue) {
    this.externalValue = externalValue;
  }

  /** Returns the version value used in JSON configuration. */
  public String externalValue() {
    return externalValue;
  }

  /** Resolves an exact supported version value. */
  public static TransformationSchemaVersion fromExternalValue(String value) {
    for (TransformationSchemaVersion version : values()) {
      if (version.externalValue.equals(value)) {
        return version;
      }
    }
    throw new IllegalArgumentException("unsupported transformation schema version: " + value);
  }
}
