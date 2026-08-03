package io.streamforge.transform.blueprint;

/** Supported output blueprint schema versions. */
public enum BlueprintSchemaVersion {
  V1_0("1.0");

  private final String externalValue;

  BlueprintSchemaVersion(String externalValue) {
    this.externalValue = externalValue;
  }

  public String externalValue() {
    return externalValue;
  }

  static BlueprintSchemaVersion parse(String value) {
    if (V1_0.externalValue.equals(value)) {
      return V1_0;
    }
    throw new IllegalArgumentException("unsupported output blueprint schema version: " + value);
  }
}
