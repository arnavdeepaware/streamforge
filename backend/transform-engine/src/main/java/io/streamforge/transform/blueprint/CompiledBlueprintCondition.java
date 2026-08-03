package io.streamforge.transform.blueprint;

import io.streamforge.transform.config.ComparisonOperator;
import io.streamforge.transform.config.TypedValue;
import java.util.List;

/** Field-resolved condition tree that can only compare typed literals. */
public sealed interface CompiledBlueprintCondition
    permits CompiledBlueprintCondition.Comparison,
        CompiledBlueprintCondition.All,
        CompiledBlueprintCondition.Any,
        CompiledBlueprintCondition.Not {
  record Comparison(
      CompiledBlueprintReference reference, ComparisonOperator operator, TypedValue value)
      implements CompiledBlueprintCondition {}

  record All(List<CompiledBlueprintCondition> conditions) implements CompiledBlueprintCondition {
    public All {
      conditions = List.copyOf(conditions);
    }
  }

  record Any(List<CompiledBlueprintCondition> conditions) implements CompiledBlueprintCondition {
    public Any {
      conditions = List.copyOf(conditions);
    }
  }

  record Not(CompiledBlueprintCondition condition) implements CompiledBlueprintCondition {}
}
