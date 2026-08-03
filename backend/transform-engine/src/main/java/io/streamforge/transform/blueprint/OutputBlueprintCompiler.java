package io.streamforge.transform.blueprint;

import io.streamforge.transform.compile.FieldDefinition;
import io.streamforge.transform.compile.TransformationFieldSchema;
import io.streamforge.transform.config.ComparisonOperator;
import io.streamforge.transform.config.FieldType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves every blueprint reference and type rule before pipeline startup. */
public final class OutputBlueprintCompiler {
  public CompiledOutputBlueprint compile(
      OutputBlueprint blueprint,
      TransformationFieldSchema canonicalSchema,
      Optional<TransformationFieldSchema> transformedSchema,
      OutputBlueprintLimits limits)
      throws OutputBlueprintValidationException {
    if (blueprint == null || canonicalSchema == null || transformedSchema == null || limits == null)
      throw new IllegalArgumentException("blueprint schemas and limits must not be null");
    Counter counter = new Counter(limits);
    CompiledOutputBlueprintValue value =
        compileValue(blueprint.output(), "$", canonicalSchema, transformedSchema, 0, counter);
    return new CompiledOutputBlueprint(
        blueprint.schemaVersion(), (CompiledOutputBlueprintValue.ObjectValue) value, limits);
  }

  private CompiledOutputBlueprintValue compileValue(
      OutputBlueprintValue value,
      String location,
      TransformationFieldSchema canonical,
      Optional<TransformationFieldSchema> transformed,
      int depth,
      Counter counter)
      throws OutputBlueprintValidationException {
    if (depth > counter.limits.maxDepth())
      throw failure(
          BlueprintValidationCode.LIMIT_EXCEEDED,
          location,
          "blueprint depth exceeds " + counter.limits.maxDepth());
    return switch (value) {
      case OutputBlueprintValue.Reference reference ->
          new CompiledOutputBlueprintValue.Reference(
              resolve(reference.reference(), location, canonical, transformed));
      case OutputBlueprintValue.Literal literal ->
          new CompiledOutputBlueprintValue.Literal(literal.value());
      case OutputBlueprintValue.Formatted formatted -> {
        CompiledBlueprintReference reference =
            resolve(formatted.reference(), location, canonical, transformed);
        FieldType expected =
            formatted.format() == BlueprintFormat.FIXED_DECIMAL_PLAIN
                ? FieldType.FIXED_DECIMAL
                : FieldType.TIMESTAMP_NANOS;
        if (reference.field().type() != expected)
          throw failure(
              BlueprintValidationCode.UNSUPPORTED_FORMAT,
              location,
              formatted.format() + " requires " + expected);
        yield new CompiledOutputBlueprintValue.Formatted(reference, formatted.format());
      }
      case OutputBlueprintValue.ObjectValue object -> {
        LinkedHashMap<String, CompiledOutputBlueprintValue> fields = new LinkedHashMap<>();
        for (Map.Entry<String, OutputBlueprintValue> field : object.fields().entrySet()) {
          counter.add(location + "." + field.getKey());
          fields.put(
              field.getKey(),
              compileValue(
                  field.getValue(),
                  location + "." + field.getKey(),
                  canonical,
                  transformed,
                  depth + 1,
                  counter));
        }
        yield new CompiledOutputBlueprintValue.ObjectValue(fields);
      }
      case OutputBlueprintValue.ArrayValue array -> {
        List<CompiledOutputBlueprintValue> items = new ArrayList<>();
        for (int index = 0; index < array.items().size(); index++) {
          counter.add(location + "[" + index + "]");
          items.add(
              compileValue(
                  array.items().get(index),
                  location + "[" + index + "]",
                  canonical,
                  transformed,
                  depth + 1,
                  counter));
        }
        yield new CompiledOutputBlueprintValue.ArrayValue(items);
      }
      case OutputBlueprintValue.Conditional conditional ->
          new CompiledOutputBlueprintValue.Conditional(
              compileCondition(
                  conditional.condition(), location + ".condition", canonical, transformed),
              compileValue(
                  conditional.value(),
                  location + ".value",
                  canonical,
                  transformed,
                  depth + 1,
                  counter));
    };
  }

  private CompiledBlueprintCondition compileCondition(
      BlueprintCondition condition,
      String location,
      TransformationFieldSchema canonical,
      Optional<TransformationFieldSchema> transformed)
      throws OutputBlueprintValidationException {
    return switch (condition) {
      case BlueprintCondition.Comparison comparison -> {
        CompiledBlueprintReference reference =
            resolve(comparison.reference(), location, canonical, transformed);
        if (reference.field().type() != comparison.value().type())
          throw failure(
              BlueprintValidationCode.TYPE_MISMATCH,
              location,
              "condition literal type does not match " + reference.field().type());
        if (reference.field().type() == FieldType.OBJECT
            || (comparison.operator() != ComparisonOperator.EQ
                && comparison.operator() != ComparisonOperator.NE
                && reference.field().type() != FieldType.STRING
                && reference.field().type() != FieldType.INT64
                && reference.field().type() != FieldType.FIXED_DECIMAL
                && reference.field().type() != FieldType.TIMESTAMP_NANOS))
          throw failure(
              BlueprintValidationCode.TYPE_MISMATCH,
              location,
              "comparison operator is not supported for " + reference.field().type());
        yield new CompiledBlueprintCondition.Comparison(
            reference, comparison.operator(), comparison.value());
      }
      case BlueprintCondition.All all ->
          new CompiledBlueprintCondition.All(
              compileConditions(all.conditions(), location, canonical, transformed));
      case BlueprintCondition.Any any ->
          new CompiledBlueprintCondition.Any(
              compileConditions(any.conditions(), location, canonical, transformed));
      case BlueprintCondition.Not not ->
          new CompiledBlueprintCondition.Not(
              compileCondition(not.condition(), location, canonical, transformed));
    };
  }

  private CompiledBlueprintReference resolve(
      BlueprintReference reference,
      String location,
      TransformationFieldSchema canonical,
      Optional<TransformationFieldSchema> transformed)
      throws OutputBlueprintValidationException {
    TransformationFieldSchema schema;
    if (reference.source() == BlueprintSource.CANONICAL) schema = canonical;
    else if (transformed.isPresent()) schema = transformed.get();
    else
      throw failure(
          BlueprintValidationCode.TRANSFORMED_SCHEMA_REQUIRED,
          location,
          "transformed reference requires a compiled transformation schema");
    FieldDefinition field = schema.fields().get(reference.path());
    if (field == null)
      throw failure(
          BlueprintValidationCode.UNKNOWN_FIELD,
          location,
          "unknown " + reference.source().externalValue() + " field: " + reference.path());
    return new CompiledBlueprintReference(reference.source(), field);
  }

  private static OutputBlueprintValidationException failure(
      BlueprintValidationCode code, String location, String detail) {
    return new OutputBlueprintValidationException(code, location, detail);
  }

  private List<CompiledBlueprintCondition> compileConditions(
      List<BlueprintCondition> conditions,
      String location,
      TransformationFieldSchema canonical,
      Optional<TransformationFieldSchema> transformed)
      throws OutputBlueprintValidationException {
    List<CompiledBlueprintCondition> compiled = new ArrayList<>();
    for (BlueprintCondition condition : conditions)
      compiled.add(compileCondition(condition, location, canonical, transformed));
    return compiled;
  }

  private static final class Counter {
    private final OutputBlueprintLimits limits;
    private int fields;

    private Counter(OutputBlueprintLimits limits) {
      this.limits = limits;
    }

    private void add(String location) throws OutputBlueprintValidationException {
      if (++fields > limits.maxFieldCount())
        throw failure(
            BlueprintValidationCode.LIMIT_EXCEEDED,
            location,
            "blueprint fields exceed " + limits.maxFieldCount());
    }
  }
}
