package io.streamforge.pipelineruntime.output;

import io.streamforge.common.model.FixedDecimal;
import io.streamforge.transform.blueprint.OutputBlueprintDocument;
import io.streamforge.transform.execute.CanonicalEventDocument;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable JSON-shaped record accepted by local output sinks. */
public final class OutputRecord {
  private final Map<String, Object> fields;

  public OutputRecord(Map<String, Object> fields) {
    this.fields = immutableObject(fields);
  }

  /** Creates an output record from a successful transformation document. */
  public static OutputRecord from(CanonicalEventDocument document) {
    if (document == null) {
      throw new IllegalArgumentException("transformed document must not be null");
    }
    return new OutputRecord(document.root());
  }

  /** Creates an output record from a rendered output blueprint. */
  public static OutputRecord from(OutputBlueprintDocument document) {
    if (document == null) {
      throw new IllegalArgumentException("blueprint document must not be null");
    }
    return new OutputRecord(document.root());
  }

  /** Returns deterministic, immutable object fields. */
  public Map<String, Object> fields() {
    return fields;
  }

  private static Map<String, Object> immutableObject(Map<String, Object> source) {
    if (source == null) {
      throw new IllegalArgumentException("output record fields must not be null");
    }
    List<Map.Entry<String, Object>> entries = new ArrayList<>(source.entrySet());
    for (Map.Entry<String, Object> entry : entries) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        throw new IllegalArgumentException("output record field names must be non-blank");
      }
    }
    entries.sort(Comparator.comparing(Map.Entry::getKey));
    LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : entries) {
      String key = entry.getKey();
      copy.put(key, immutableValue(entry.getValue()));
    }
    return Collections.unmodifiableMap(copy);
  }

  private static Object immutableValue(Object value) {
    if (value instanceof Map<?, ?> object) {
      LinkedHashMap<String, Object> typed = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : object.entrySet()) {
        if (!(entry.getKey() instanceof String key)) {
          throw new IllegalArgumentException("output record object keys must be strings");
        }
        typed.put(key, entry.getValue());
      }
      return immutableObject(typed);
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>(list.size());
      for (Object item : list) {
        copy.add(immutableValue(item));
      }
      return List.copyOf(copy);
    }
    if (value instanceof String
        || value instanceof Boolean
        || value instanceof Long
        || value instanceof FixedDecimal) {
      return value;
    }
    throw new IllegalArgumentException("output record contains an unsupported value type");
  }
}
