package io.streamforge.transform.blueprint;

import io.streamforge.transform.config.FieldPath;

/** Explicit reference to one canonical or transformed field. */
public record BlueprintReference(BlueprintSource source, FieldPath path) {
  public BlueprintReference {
    if (source == null || path == null) {
      throw new IllegalArgumentException("blueprint reference source and path must not be null");
    }
  }
}
