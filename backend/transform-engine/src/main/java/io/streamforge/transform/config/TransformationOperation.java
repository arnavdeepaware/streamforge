package io.streamforge.transform.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Closed set of raw, validated transformation operation configurations. */
public sealed interface TransformationOperation
    permits TransformationOperation.Select,
        TransformationOperation.Rename,
        TransformationOperation.Remove,
        TransformationOperation.AddConstant,
        TransformationOperation.Cast,
        TransformationOperation.ScaleFixedDecimal,
        TransformationOperation.EnumMap,
        TransformationOperation.Filter,
        TransformationOperation.CreateObject,
        TransformationOperation.ConditionalField {

  record Select(List<FieldPath> fields) implements TransformationOperation {
    public Select {
      if (fields == null || fields.isEmpty() || fields.stream().anyMatch(field -> field == null)) {
        throw new IllegalArgumentException("select must contain non-null fields");
      }
      fields = List.copyOf(fields);
    }
  }

  record Rename(FieldPath from, FieldPath to) implements TransformationOperation {
    public Rename {
      requirePaths(from, to);
    }
  }

  record Remove(FieldPath path) implements TransformationOperation {
    public Remove {
      requirePath(path);
    }
  }

  record AddConstant(FieldPath path, TypedValue value) implements TransformationOperation {
    public AddConstant {
      requirePath(path);
      if (value == null) {
        throw new IllegalArgumentException("constant value must not be null");
      }
    }
  }

  record Cast(FieldPath path, FieldType targetType) implements TransformationOperation {
    public Cast {
      requirePath(path);
      if (targetType == null || targetType == FieldType.OBJECT) {
        throw new IllegalArgumentException("cast target must be a scalar field type");
      }
    }
  }

  record ScaleFixedDecimal(FieldPath path, int targetScale) implements TransformationOperation {
    public ScaleFixedDecimal {
      requirePath(path);
      if (targetScale < 0 || targetScale > 18) {
        throw new IllegalArgumentException("fixed-decimal target scale must be between 0 and 18");
      }
    }
  }

  record EnumMap(FieldPath path, Map<String, String> mapping) implements TransformationOperation {
    public EnumMap {
      requirePath(path);
      if (mapping == null || mapping.isEmpty()) {
        throw new IllegalArgumentException("enum mapping must not be empty");
      }
      LinkedHashMap<String, String> copy = new LinkedHashMap<>();
      mapping.forEach(
          (key, value) -> {
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
              throw new IllegalArgumentException("enum mapping entries must not be blank");
            }
            copy.put(key, value);
          });
      mapping = Map.copyOf(copy);
    }
  }

  record Filter(Condition condition) implements TransformationOperation {
    public Filter {
      if (condition == null) {
        throw new IllegalArgumentException("filter condition must not be null");
      }
    }
  }

  record CreateObject(FieldPath path) implements TransformationOperation {
    public CreateObject {
      requirePath(path);
    }
  }

  record ConditionalField(
      FieldPath path, Condition condition, TypedValue whenTrue, TypedValue whenFalse)
      implements TransformationOperation {
    public ConditionalField {
      requirePath(path);
      if (condition == null || whenTrue == null || whenFalse == null) {
        throw new IllegalArgumentException("conditional field values must not be null");
      }
      if (whenTrue.type() != whenFalse.type()) {
        throw new IllegalArgumentException("conditional branches must have the same type");
      }
    }
  }

  private static void requirePath(FieldPath path) {
    if (path == null) {
      throw new IllegalArgumentException("field path must not be null");
    }
  }

  private static void requirePaths(FieldPath from, FieldPath to) {
    requirePath(from);
    requirePath(to);
    if (from.equals(to)) {
      throw new IllegalArgumentException("rename source and target must differ");
    }
  }
}
