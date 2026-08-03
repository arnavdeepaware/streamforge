package io.streamforge.transform.compile;

import io.streamforge.transform.config.FieldPath;
import io.streamforge.transform.config.FieldType;

/** Resolved field path, type, and mutation policy used during compilation. */
public record FieldDefinition(FieldPath path, FieldType type, boolean protectedField) {

  public FieldDefinition {
    if (path == null || type == null) {
      throw new IllegalArgumentException("field definition path and type must not be null");
    }
  }

  FieldDefinition withPath(FieldPath newPath) {
    return new FieldDefinition(newPath, type, protectedField);
  }

  FieldDefinition withType(FieldType newType) {
    return new FieldDefinition(path, newType, protectedField);
  }
}
