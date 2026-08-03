package io.streamforge.transform.config;

import java.util.List;

/** Restricted condition tree with comparisons and bounded boolean composition only. */
public sealed interface Condition
    permits Condition.Comparison, Condition.All, Condition.Any, Condition.Not {

  record Comparison(FieldPath field, ComparisonOperator operator, TypedValue value)
      implements Condition {
    public Comparison {
      if (field == null || operator == null || value == null) {
        throw new IllegalArgumentException("comparison fields must not be null");
      }
    }
  }

  record All(List<Condition> conditions) implements Condition {
    public All {
      conditions = immutableConditions(conditions, "all");
    }
  }

  record Any(List<Condition> conditions) implements Condition {
    public Any {
      conditions = immutableConditions(conditions, "any");
    }
  }

  record Not(Condition condition) implements Condition {
    public Not {
      if (condition == null) {
        throw new IllegalArgumentException("not condition must not be null");
      }
    }
  }

  private static List<Condition> immutableConditions(List<Condition> conditions, String kind) {
    if (conditions == null
        || conditions.isEmpty()
        || conditions.stream().anyMatch(c -> c == null)) {
      throw new IllegalArgumentException(kind + " condition must contain non-null children");
    }
    return List.copyOf(conditions);
  }
}
