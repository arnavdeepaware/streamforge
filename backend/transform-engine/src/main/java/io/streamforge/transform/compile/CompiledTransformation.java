package io.streamforge.transform.compile;

import io.streamforge.transform.config.TransformationSchemaVersion;
import java.util.List;

/** Immutable, field-resolved rules separated from the raw JSON configuration model. */
public record CompiledTransformation(
    TransformationSchemaVersion schemaVersion,
    List<CompiledOperation> operations,
    TransformationFieldSchema outputSchema) {

  public CompiledTransformation {
    if (schemaVersion == null || operations == null || outputSchema == null) {
      throw new IllegalArgumentException("compiled transformation fields must not be null");
    }
    operations = List.copyOf(operations);
  }
}
