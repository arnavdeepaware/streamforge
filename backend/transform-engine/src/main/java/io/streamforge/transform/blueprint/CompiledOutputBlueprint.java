package io.streamforge.transform.blueprint;

/** Immutable compiled output blueprint that is safe to reuse for every event. */
public record CompiledOutputBlueprint(
    BlueprintSchemaVersion schemaVersion,
    CompiledOutputBlueprintValue.ObjectValue output,
    OutputBlueprintLimits limits) {
  public CompiledOutputBlueprint {
    if (schemaVersion == null || output == null || limits == null)
      throw new IllegalArgumentException("compiled blueprint fields must not be null");
  }
}
