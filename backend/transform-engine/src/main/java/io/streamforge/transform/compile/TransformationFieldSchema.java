package io.streamforge.transform.compile;

import io.streamforge.transform.config.FieldPath;
import io.streamforge.transform.config.FieldType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable field catalog used to validate a transformation before activation. */
public final class TransformationFieldSchema {

  private final Map<FieldPath, FieldDefinition> fields;

  private TransformationFieldSchema(Map<FieldPath, FieldDefinition> fields) {
    this.fields = Map.copyOf(fields);
  }

  /** Creates a schema and verifies unique paths and object-valued parent paths. */
  public static TransformationFieldSchema of(Collection<FieldDefinition> definitions) {
    if (definitions == null || definitions.isEmpty()) {
      throw new IllegalArgumentException("field schema must not be empty");
    }
    LinkedHashMap<FieldPath, FieldDefinition> fields = new LinkedHashMap<>();
    for (FieldDefinition definition : definitions) {
      if (definition == null || fields.putIfAbsent(definition.path(), definition) != null) {
        throw new IllegalArgumentException("field schema contains null or duplicate paths");
      }
    }
    for (FieldDefinition definition : definitions) {
      FieldPath parent = definition.path().parent();
      if (parent != null) {
        FieldDefinition parentDefinition = fields.get(parent);
        if (parentDefinition == null || parentDefinition.type() != FieldType.OBJECT) {
          throw new IllegalArgumentException(
              "field parent must exist and be OBJECT: " + definition.path());
        }
      }
    }
    return new TransformationFieldSchema(fields);
  }

  public Map<FieldPath, FieldDefinition> fields() {
    return fields;
  }
}
