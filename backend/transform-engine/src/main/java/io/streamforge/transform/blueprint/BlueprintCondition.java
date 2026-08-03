package io.streamforge.transform.blueprint;

import io.streamforge.transform.config.ComparisonOperator;
import io.streamforge.transform.config.TypedValue;
import java.util.List;

/** Closed comparison tree used only for conditional blueprint inclusion. */
public sealed interface BlueprintCondition
    permits BlueprintCondition.Comparison,
        BlueprintCondition.All,
        BlueprintCondition.Any,
        BlueprintCondition.Not {

  record Comparison(BlueprintReference reference, ComparisonOperator operator, TypedValue value)
      implements BlueprintCondition {
    public Comparison {
      if (reference == null || operator == null || value == null) {
        throw new IllegalArgumentException("blueprint comparison fields must not be null");
      }
    }
  }

  record All(List<BlueprintCondition> conditions) implements BlueprintCondition {
    public All {
      conditions = immutableConditions(conditions, "all");
    }
  }

  record Any(List<BlueprintCondition> conditions) implements BlueprintCondition {
    public Any {
      conditions = immutableConditions(conditions, "any");
    }
  }

  record Not(BlueprintCondition condition) implements BlueprintCondition {
    public Not {
      if (condition == null) {
        throw new IllegalArgumentException("blueprint not condition must not be null");
      }
    }
  }

  private static List<BlueprintCondition> immutableConditions(
      List<BlueprintCondition> conditions, String kind) {
    if (conditions == null
        || conditions.isEmpty()
        || conditions.stream().anyMatch(c -> c == null)) {
      throw new IllegalArgumentException(kind + " condition must contain non-null children");
    }
    return List.copyOf(conditions);
  }
}
