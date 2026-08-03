package io.streamforge.transform.blueprint;

/** Raw blueprint configuration with an object root suitable for a JSON output document. */
public record OutputBlueprint(
    BlueprintSchemaVersion schemaVersion, OutputBlueprintValue.ObjectValue output) {
  public OutputBlueprint {
    if (schemaVersion == null || output == null) {
      throw new IllegalArgumentException("blueprint schema version and output must not be null");
    }
  }
}
