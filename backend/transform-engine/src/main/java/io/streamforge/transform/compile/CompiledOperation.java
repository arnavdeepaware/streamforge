package io.streamforge.transform.compile;

import io.streamforge.transform.config.FieldPath;
import io.streamforge.transform.config.FieldType;
import io.streamforge.transform.config.TypedValue;
import java.util.List;
import java.util.Map;

/** Closed set of field-resolved operations consumed by the deterministic executor. */
public sealed interface CompiledOperation
    permits CompiledOperation.Select,
        CompiledOperation.Rename,
        CompiledOperation.Remove,
        CompiledOperation.AddConstant,
        CompiledOperation.Cast,
        CompiledOperation.ScaleFixedDecimal,
        CompiledOperation.EnumMap,
        CompiledOperation.Filter,
        CompiledOperation.CreateObject,
        CompiledOperation.ConditionalField {

  record Select(List<FieldDefinition> fields) implements CompiledOperation {
    public Select {
      fields = List.copyOf(fields);
    }
  }

  record Rename(FieldDefinition source, FieldPath target) implements CompiledOperation {}

  record Remove(FieldDefinition field) implements CompiledOperation {}

  record AddConstant(FieldPath path, TypedValue value) implements CompiledOperation {}

  record Cast(FieldDefinition field, FieldType targetType) implements CompiledOperation {}

  record ScaleFixedDecimal(FieldDefinition field, int targetScale) implements CompiledOperation {}

  record EnumMap(FieldDefinition field, Map<String, String> mapping) implements CompiledOperation {
    public EnumMap {
      mapping = Map.copyOf(mapping);
    }
  }

  record Filter(CompiledCondition condition) implements CompiledOperation {}

  record CreateObject(FieldPath path) implements CompiledOperation {}

  record ConditionalField(
      FieldPath path, CompiledCondition condition, TypedValue whenTrue, TypedValue whenFalse)
      implements CompiledOperation {}
}
