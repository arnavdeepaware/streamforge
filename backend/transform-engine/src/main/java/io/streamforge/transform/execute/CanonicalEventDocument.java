package io.streamforge.transform.execute;

import io.streamforge.common.model.FixedDecimal;
import io.streamforge.transform.config.FieldPath;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable typed document view used as a transformation result boundary. */
public final class CanonicalEventDocument {

  private final Map<String, Object> root;

  private CanonicalEventDocument(Map<String, Object> root) {
    this.root = immutableObject(root);
  }

  static CanonicalEventDocument fromMutable(Map<String, Object> root) {
    return new CanonicalEventDocument(root);
  }

  /** Returns an immutable root document containing only safe scalar values and nested objects. */
  public Map<String, Object> root() {
    return root;
  }

  /** Returns a value at a dotted path when that value is present in this document. */
  public Optional<Object> valueAt(FieldPath path) {
    if (path == null) {
      throw new IllegalArgumentException("field path must not be null");
    }
    Object current = root;
    for (String segment : path.segments()) {
      if (!(current instanceof Map<?, ?> object) || !object.containsKey(segment)) {
        return Optional.empty();
      }
      current = object.get(segment);
    }
    return Optional.of(current);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> immutableObject(Map<String, Object> source) {
    if (source == null) {
      throw new IllegalArgumentException("document root must not be null");
    }
    LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
    source.forEach(
        (key, value) -> {
          if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("document keys must not be blank");
          }
          copy.put(key, immutableValue(value));
        });
    return Collections.unmodifiableMap(copy);
  }

  @SuppressWarnings("unchecked")
  private static Object immutableValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      LinkedHashMap<String, Object> typed = new LinkedHashMap<>();
      map.forEach(
          (key, nestedValue) -> {
            if (!(key instanceof String stringKey)) {
              throw new IllegalArgumentException("document object keys must be strings");
            }
            typed.put(stringKey, nestedValue);
          });
      return immutableObject(typed);
    }
    if (value instanceof String
        || value instanceof Boolean
        || value instanceof Long
        || value instanceof FixedDecimal) {
      return value;
    }
    throw new IllegalArgumentException("document contains unsupported value type");
  }
}
