package io.streamforge.transform.execute;

import io.streamforge.transform.config.FieldPath;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Package-private mutable workspace that is frozen before a transformed result is returned. */
final class MutableDocument {

  private final LinkedHashMap<String, Object> root;

  MutableDocument(Map<String, Object> source) {
    root = copyObject(source);
  }

  Object required(FieldPath path) throws DocumentAccessException {
    Parent parent = parent(path);
    if (!parent.object.containsKey(parent.leaf)) {
      throw new DocumentAccessException(
          TransformationFailureCode.MISSING_FIELD,
          path.toString(),
          "field is absent from this event");
    }
    return parent.object.get(parent.leaf);
  }

  void replace(FieldPath path, Object value) throws DocumentAccessException {
    Parent parent = parent(path);
    if (!parent.object.containsKey(parent.leaf)) {
      throw new DocumentAccessException(
          TransformationFailureCode.MISSING_FIELD,
          path.toString(),
          "field is absent from this event");
    }
    parent.object.put(parent.leaf, copyValue(value));
  }

  void add(FieldPath path, Object value) throws DocumentAccessException {
    Parent parent = parent(path);
    if (parent.object.containsKey(parent.leaf)) {
      throw new DocumentAccessException(
          TransformationFailureCode.INVALID_DOCUMENT,
          path.toString(),
          "target field already exists in this event");
    }
    parent.object.put(parent.leaf, copyValue(value));
  }

  void remove(FieldPath path) throws DocumentAccessException {
    Parent parent = parent(path);
    if (!parent.object.containsKey(parent.leaf)) {
      throw new DocumentAccessException(
          TransformationFailureCode.MISSING_FIELD,
          path.toString(),
          "field is absent from this event");
    }
    parent.object.remove(parent.leaf);
  }

  void rename(FieldPath from, FieldPath to) throws DocumentAccessException {
    Object value = required(from);
    remove(from);
    add(to, value);
  }

  void retain(Set<FieldPath> retainedPaths) {
    retainObject(root, "", retainedPaths);
  }

  int nestingDepth() {
    return nestingDepth(root, 0);
  }

  int fieldCount() {
    return fieldCount(root);
  }

  CanonicalEventDocument freeze() {
    return CanonicalEventDocument.fromMutable(root);
  }

  @SuppressWarnings("unchecked")
  private Parent parent(FieldPath path) throws DocumentAccessException {
    Map<String, Object> current = root;
    var segments = path.segments();
    for (int index = 0; index < segments.size() - 1; index++) {
      String segment = segments.get(index);
      Object child = current.get(segment);
      if (!(child instanceof Map<?, ?> map)) {
        throw new DocumentAccessException(
            TransformationFailureCode.MISSING_FIELD,
            path.toString(),
            "object parent is absent from this event");
      }
      current = (Map<String, Object>) map;
    }
    return new Parent(current, segments.getLast());
  }

  @SuppressWarnings("unchecked")
  private static LinkedHashMap<String, Object> copyObject(Map<String, Object> source) {
    LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
    source.forEach(
        (key, value) -> {
          if (value instanceof Map<?, ?> nested) {
            LinkedHashMap<String, Object> nestedCopy = new LinkedHashMap<>();
            nested.forEach(
                (nestedKey, nestedValue) ->
                    nestedCopy.put((String) nestedKey, copyValue(nestedValue)));
            copy.put(key, nestedCopy);
          } else {
            copy.put(key, value);
          }
        });
    return copy;
  }

  @SuppressWarnings("unchecked")
  private static Object copyValue(Object value) {
    if (value instanceof Map<?, ?> nested) {
      LinkedHashMap<String, Object> nestedCopy = new LinkedHashMap<>();
      nested.forEach(
          (nestedKey, nestedValue) -> nestedCopy.put((String) nestedKey, copyValue(nestedValue)));
      return nestedCopy;
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private static void retainObject(
      Map<String, Object> object, String prefix, Set<FieldPath> retainedPaths) {
    object
        .entrySet()
        .removeIf(
            entry -> {
              FieldPath path =
                  new FieldPath(prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey());
              boolean retained = retainedPaths.stream().anyMatch(path::isSameOrDescendantOf);
              if (!retained) {
                return true;
              }
              Object value = entry.getValue();
              if (value instanceof Map<?, ?> nested) {
                retainObject((Map<String, Object>) nested, path.value(), retainedPaths);
              }
              return false;
            });
  }

  private static int nestingDepth(Map<String, Object> object, int currentDepth) {
    int maximum = currentDepth;
    for (Object value : object.values()) {
      if (value instanceof Map<?, ?> nested) {
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) nested;
        maximum = Math.max(maximum, nestingDepth(typed, currentDepth + 1));
      }
    }
    return maximum;
  }

  private static int fieldCount(Map<String, Object> object) {
    int count = 0;
    for (Object value : object.values()) {
      count++;
      if (value instanceof Map<?, ?> nested) {
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) nested;
        count += fieldCount(typed);
      }
    }
    return count;
  }

  private record Parent(Map<String, Object> object, String leaf) {}
}
