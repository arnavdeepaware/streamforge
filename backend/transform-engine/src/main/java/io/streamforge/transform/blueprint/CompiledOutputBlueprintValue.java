package io.streamforge.transform.blueprint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Closed field-resolved blueprint value tree consumed by the renderer. */
public sealed interface CompiledOutputBlueprintValue
    permits CompiledOutputBlueprintValue.Reference,
        CompiledOutputBlueprintValue.Literal,
        CompiledOutputBlueprintValue.ObjectValue,
        CompiledOutputBlueprintValue.ArrayValue,
        CompiledOutputBlueprintValue.Formatted,
        CompiledOutputBlueprintValue.Conditional {
  record Reference(CompiledBlueprintReference reference) implements CompiledOutputBlueprintValue {}

  record Literal(Object value) implements CompiledOutputBlueprintValue {}

  record ObjectValue(Map<String, CompiledOutputBlueprintValue> fields)
      implements CompiledOutputBlueprintValue {
    public ObjectValue {
      fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
  }

  record ArrayValue(List<CompiledOutputBlueprintValue> items)
      implements CompiledOutputBlueprintValue {
    public ArrayValue {
      items = List.copyOf(items);
    }
  }

  record Formatted(CompiledBlueprintReference reference, BlueprintFormat format)
      implements CompiledOutputBlueprintValue {}

  record Conditional(CompiledBlueprintCondition condition, CompiledOutputBlueprintValue value)
      implements CompiledOutputBlueprintValue {}
}
