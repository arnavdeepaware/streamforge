package io.streamforge.transform.blueprint;

import io.streamforge.common.model.FixedDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Closed raw output blueprint values; no template syntax or executable expressions are present. */
public sealed interface OutputBlueprintValue
    permits OutputBlueprintValue.Reference,
        OutputBlueprintValue.Literal,
        OutputBlueprintValue.ObjectValue,
        OutputBlueprintValue.ArrayValue,
        OutputBlueprintValue.Formatted,
        OutputBlueprintValue.Conditional {

  record Reference(BlueprintReference reference) implements OutputBlueprintValue {
    public Reference {
      if (reference == null) {
        throw new IllegalArgumentException("blueprint reference must not be null");
      }
    }
  }

  /** A literal is one exact JSON scalar, never a floating-point value or arbitrary object. */
  record Literal(Object value) implements OutputBlueprintValue {
    public Literal {
      if (!(value instanceof String
          || value instanceof Boolean
          || value instanceof Long
          || value instanceof FixedDecimal)) {
        throw new IllegalArgumentException("blueprint literal must be a supported exact scalar");
      }
    }
  }

  record ObjectValue(Map<String, OutputBlueprintValue> fields) implements OutputBlueprintValue {
    public ObjectValue {
      if (fields == null) {
        throw new IllegalArgumentException("blueprint object fields must not be null");
      }
      LinkedHashMap<String, OutputBlueprintValue> copy = new LinkedHashMap<>();
      fields.forEach(
          (key, value) -> {
            if (key == null || key.isBlank() || value == null) {
              throw new IllegalArgumentException(
                  "blueprint object keys and values must be present");
            }
            copy.put(key, value);
          });
      fields = Collections.unmodifiableMap(copy);
    }
  }

  record ArrayValue(List<OutputBlueprintValue> items) implements OutputBlueprintValue {
    public ArrayValue {
      if (items == null || items.stream().anyMatch(item -> item == null)) {
        throw new IllegalArgumentException("blueprint array items must be non-null");
      }
      items = List.copyOf(items);
    }
  }

  record Formatted(BlueprintReference reference, BlueprintFormat format)
      implements OutputBlueprintValue {
    public Formatted {
      if (reference == null || format == null) {
        throw new IllegalArgumentException(
            "formatted blueprint reference and format must not be null");
      }
    }
  }

  record Conditional(BlueprintCondition condition, OutputBlueprintValue value)
      implements OutputBlueprintValue {
    public Conditional {
      if (condition == null || value == null) {
        throw new IllegalArgumentException("conditional blueprint fields must not be null");
      }
    }
  }
}
