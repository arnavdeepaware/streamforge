package io.streamforge.transform.blueprint;

import io.streamforge.transform.compile.FieldDefinition;

/** Source and field definition resolved before any event is rendered. */
public record CompiledBlueprintReference(BlueprintSource source, FieldDefinition field) {
  public CompiledBlueprintReference {
    if (source == null || field == null)
      throw new IllegalArgumentException("compiled blueprint reference must be complete");
  }
}
