package io.streamforge.transform.config;

import java.util.List;

/** Immutable raw transformation configuration produced by strict JSON parsing. */
public record TransformationConfig(
    TransformationSchemaVersion schemaVersion, List<TransformationOperation> operations) {

  public TransformationConfig {
    if (schemaVersion == null || operations == null) {
      throw new IllegalArgumentException("configuration fields must not be null");
    }
    if (operations.isEmpty() || operations.stream().anyMatch(operation -> operation == null)) {
      throw new IllegalArgumentException("configuration must contain non-null operations");
    }
    operations = List.copyOf(operations);
  }
}
