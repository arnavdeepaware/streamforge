package io.streamforge.transform.blueprint;

import io.streamforge.common.model.FixedDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable nested output preserving booleans, exact integers, and fixed decimals. */
public final class OutputBlueprintDocument {
  private final Map<String, Object> root;

  OutputBlueprintDocument(Map<String, Object> root) {
    this.root = immutableObject(root);
  }

  public Map<String, Object> root() {
    return root;
  }

  private static Map<String, Object> immutableObject(Map<String, Object> values) {
    LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
    values.forEach((key, value) -> copy.put(key, immutableValue(value)));
    return Collections.unmodifiableMap(copy);
  }

  private static Object immutableValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      LinkedHashMap<String, Object> typed = new LinkedHashMap<>();
      map.forEach((key, nested) -> typed.put((String) key, nested));
      return immutableObject(typed);
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>();
      for (Object item : list) copy.add(immutableValue(item));
      return List.copyOf(copy);
    }
    if (value instanceof String
        || value instanceof Boolean
        || value instanceof Long
        || value instanceof FixedDecimal) return value;
    throw new IllegalArgumentException("output blueprint contains an unsupported value type");
  }
}
