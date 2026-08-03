package io.streamforge.transform.blueprint;

/** The field document from which a blueprint reference is resolved. */
public enum BlueprintSource {
  CANONICAL("canonical"),
  TRANSFORMED("transformed");

  private final String externalValue;

  BlueprintSource(String externalValue) {
    this.externalValue = externalValue;
  }

  static BlueprintSource parse(String value) {
    for (BlueprintSource source : values()) {
      if (source.externalValue.equals(value)) {
        return source;
      }
    }
    throw new IllegalArgumentException("unsupported blueprint reference source: " + value);
  }

  public String externalValue() {
    return externalValue;
  }
}
