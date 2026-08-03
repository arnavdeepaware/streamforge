package io.streamforge.transform.compile;

import io.streamforge.transform.config.ComparisonOperator;
import io.streamforge.transform.config.TypedValue;
import java.util.List;

/** Field-resolved condition tree that contains no executable source expression. */
public sealed interface CompiledCondition
    permits CompiledCondition.Comparison,
        CompiledCondition.All,
        CompiledCondition.Any,
        CompiledCondition.Not {

  record Comparison(FieldDefinition field, ComparisonOperator operator, TypedValue value)
      implements CompiledCondition {
    public Comparison {
      if (field == null || operator == null || value == null) {
        throw new IllegalArgumentException("compiled comparison fields must not be null");
      }
    }
  }

  record All(List<CompiledCondition> conditions) implements CompiledCondition {
    public All {
      conditions = List.copyOf(conditions);
    }
  }

  record Any(List<CompiledCondition> conditions) implements CompiledCondition {
    public Any {
      conditions = List.copyOf(conditions);
    }
  }

  record Not(CompiledCondition condition) implements CompiledCondition {
    public Not {
      if (condition == null) {
        throw new IllegalArgumentException("compiled not condition must not be null");
      }
    }
  }
}
