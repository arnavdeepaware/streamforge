package io.streamforge.transform.compile;

import io.streamforge.transform.config.ComparisonOperator;
import io.streamforge.transform.config.Condition;
import io.streamforge.transform.config.FieldPath;
import io.streamforge.transform.config.FieldType;
import io.streamforge.transform.config.TransformationConfig;
import io.streamforge.transform.config.TransformationOperation;
import io.streamforge.transform.config.TypedValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves paths and type rules into an immutable plan before event processing begins. */
public final class TransformationCompiler {

  /** Compiles ordered rules against the supplied input field schema. */
  public CompiledTransformation compile(
      TransformationConfig config, TransformationFieldSchema inputSchema)
      throws TransformationValidationException {
    if (config == null || inputSchema == null) {
      throw new IllegalArgumentException("configuration and input schema must not be null");
    }
    LinkedHashMap<FieldPath, FieldDefinition> fields = new LinkedHashMap<>(inputSchema.fields());
    List<CompiledOperation> compiled = new ArrayList<>(config.operations().size());
    for (int index = 0; index < config.operations().size(); index++) {
      compiled.add(compileOperation(config.operations().get(index), fields, index));
    }
    return new CompiledTransformation(
        config.schemaVersion(), compiled, TransformationFieldSchema.of(fields.values()));
  }

  private CompiledOperation compileOperation(
      TransformationOperation operation,
      LinkedHashMap<FieldPath, FieldDefinition> fields,
      int operationIndex)
      throws TransformationValidationException {
    return switch (operation) {
      case TransformationOperation.Select select -> compileSelect(select, fields, operationIndex);
      case TransformationOperation.Rename rename -> compileRename(rename, fields, operationIndex);
      case TransformationOperation.Remove remove -> compileRemove(remove, fields, operationIndex);
      case TransformationOperation.AddConstant add ->
          compileAddConstant(add, fields, operationIndex);
      case TransformationOperation.Cast cast -> compileCast(cast, fields, operationIndex);
      case TransformationOperation.ScaleFixedDecimal scale ->
          compileScale(scale, fields, operationIndex);
      case TransformationOperation.EnumMap enumMap ->
          compileEnumMap(enumMap, fields, operationIndex);
      case TransformationOperation.Filter filter ->
          new CompiledOperation.Filter(
              compileCondition(filter.condition(), fields, operationIndex));
      case TransformationOperation.CreateObject create ->
          compileCreateObject(create, fields, operationIndex);
      case TransformationOperation.ConditionalField conditional ->
          compileConditionalField(conditional, fields, operationIndex);
    };
  }

  private CompiledOperation.Select compileSelect(
      TransformationOperation.Select operation,
      LinkedHashMap<FieldPath, FieldDefinition> fields,
      int operationIndex)
      throws TransformationValidationException {
    Set<FieldPath> selected = new HashSet<>();
    for (FieldPath path : operation.fields()) {
      if (!selected.add(path)) {
        throw failure(
            ValidationErrorCode.DUPLICATE_FIELD,
            operationIndex,
            "select contains duplicate field: " + path);
      }
      requireField(path, fields, operationIndex);
    }
    fields.entrySet().removeIf(entry -> !preservedBySelect(entry.getValue(), selected, fields));
    return new CompiledOperation.Select(new ArrayList<>(fields.values()));
  }

  private boolean preservedBySelect(
      FieldDefinition candidate,
      Set<FieldPath> selected,
      Map<FieldPath, FieldDefinition> allFields) {
    if (candidate.protectedField()) {
      return true;
    }
    for (FieldPath selectedPath : selected) {
      if (candidate.path().isSameOrDescendantOf(selectedPath)
          || selectedPath.isSameOrDescendantOf(candidate.path())) {
        return true;
      }
    }
    for (FieldDefinition protectedDefinition : allFields.values()) {
      if (protectedDefinition.protectedField()
          && protectedDefinition.path().isSameOrDescendantOf(candidate.path())) {
        return true;
      }
    }
    return false;
  }

  private CompiledOperation.Rename compileRename(
      TransformationOperation.Rename operation,
      LinkedHashMap<FieldPath, FieldDefinition> fields,
      int operationIndex)
      throws TransformationValidationException {
    FieldDefinition source = requireField(operation.from(), fields, operationIndex);
    requireWritableSubtree(source, fields, operationIndex);
    if (operation.to().isSameOrDescendantOf(operation.from())) {
      throw failure(
          ValidationErrorCode.INVALID_TARGET,
          operationIndex,
          "rename target must not be nested under its source: " + operation.to());
    }
    requireAvailableTarget(operation.to(), fields, operationIndex);
    requireObjectParent(operation.to(), fields, operationIndex);

    List<FieldDefinition> subtree =
        fields.values().stream()
            .filter(definition -> definition.path().isSameOrDescendantOf(operation.from()))
            .toList();
    for (FieldDefinition definition : subtree) {
      FieldPath target = definition.path().replacePrefix(operation.from(), operation.to());
      if (!definition.path().equals(operation.from()) && fields.containsKey(target)) {
        throw failure(
            ValidationErrorCode.TARGET_EXISTS,
            operationIndex,
            "rename target subtree collides with existing field: " + target);
      }
    }
    subtree.forEach(definition -> fields.remove(definition.path()));
    subtree.forEach(
        definition -> {
          FieldPath target = definition.path().replacePrefix(operation.from(), operation.to());
          fields.put(target, definition.withPath(target));
        });
    return new CompiledOperation.Rename(source, operation.to());
  }

  private CompiledOperation.Remove compileRemove(
      TransformationOperation.Remove operation,
      LinkedHashMap<FieldPath, FieldDefinition> fields,
      int operationIndex)
      throws TransformationValidationException {
    FieldDefinition field = requireField(operation.path(), fields, operationIndex);
    requireWritableSubtree(field, fields, operationIndex);
    fields.entrySet().removeIf(entry -> entry.getKey().isSameOrDescendantOf(operation.path()));
    return new CompiledOperation.Remove(field);
  }

  private CompiledOperation.AddConstant compileAddConstant(
      TransformationOperation.AddConstant operation,
      LinkedHashMap<FieldPath, FieldDefinition> fields,
      int operationIndex)
      throws TransformationValidationException {
    requireAvailableTarget(operation.path(), fields, operationIndex);
    requireObjectParent(operation.path(), fields, operationIndex);
    fields.put(
        operation.path(), new FieldDefinition(operation.path(), operation.value().type(), false));
    return new CompiledOperation.AddConstant(operation.path(), operation.value());
  }

  private CompiledOperation.Cast compileCast(
      TransformationOperation.Cast operation,
      LinkedHashMap<FieldPath, FieldDefinition> fields,
      int operationIndex)
      throws TransformationValidationException {
    FieldDefinition field = requireField(operation.path(), fields, operationIndex);
    requireWritable(field, fields, operationIndex);
    if (!castSupported(field.type(), operation.targetType())) {
      throw failure(
          ValidationErrorCode.UNSUPPORTED_CAST,
          operationIndex,
          "unsupported cast from " + field.type() + " to " + operation.targetType());
    }
    fields.put(operation.path(), field.withType(operation.targetType()));
    return new CompiledOperation.Cast(field, operation.targetType());
  }

  private CompiledOperation.ScaleFixedDecimal compileScale(
      TransformationOperation.ScaleFixedDecimal operation,
      LinkedHashMap<FieldPath, FieldDefinition> fields,
      int operationIndex)
      throws TransformationValidationException {
    FieldDefinition field = requireField(operation.path(), fields, operationIndex);
    requireWritable(field, fields, operationIndex);
    if (field.type() != FieldType.FIXED_DECIMAL) {
      throw failure(
          ValidationErrorCode.TYPE_MISMATCH,
          operationIndex,
          "scale_fixed_decimal requires FIXED_DECIMAL: " + operation.path());
    }
    return new CompiledOperation.ScaleFixedDecimal(field, operation.targetScale());
  }

  private CompiledOperation.EnumMap compileEnumMap(
      TransformationOperation.EnumMap operation,
      LinkedHashMap<FieldPath, FieldDefinition> fields,
      int operationIndex)
      throws TransformationValidationException {
    FieldDefinition field = requireField(operation.path(), fields, operationIndex);
    requireWritable(field, fields, operationIndex);
    if (field.type() != FieldType.ENUM && field.type() != FieldType.STRING) {
      throw failure(
          ValidationErrorCode.TYPE_MISMATCH,
          operationIndex,
          "enum_map requires ENUM or STRING: " + operation.path());
    }
    return new CompiledOperation.EnumMap(field, operation.mapping());
  }

  private CompiledOperation.CreateObject compileCreateObject(
      TransformationOperation.CreateObject operation,
      LinkedHashMap<FieldPath, FieldDefinition> fields,
      int operationIndex)
      throws TransformationValidationException {
    requireAvailableTarget(operation.path(), fields, operationIndex);
    requireObjectParent(operation.path(), fields, operationIndex);
    fields.put(operation.path(), new FieldDefinition(operation.path(), FieldType.OBJECT, false));
    return new CompiledOperation.CreateObject(operation.path());
  }

  private CompiledOperation.ConditionalField compileConditionalField(
      TransformationOperation.ConditionalField operation,
      LinkedHashMap<FieldPath, FieldDefinition> fields,
      int operationIndex)
      throws TransformationValidationException {
    requireAvailableTarget(operation.path(), fields, operationIndex);
    requireObjectParent(operation.path(), fields, operationIndex);
    CompiledCondition condition = compileCondition(operation.condition(), fields, operationIndex);
    fields.put(
        operation.path(),
        new FieldDefinition(operation.path(), operation.whenTrue().type(), false));
    return new CompiledOperation.ConditionalField(
        operation.path(), condition, operation.whenTrue(), operation.whenFalse());
  }

  private CompiledCondition compileCondition(
      Condition condition, Map<FieldPath, FieldDefinition> fields, int operationIndex)
      throws TransformationValidationException {
    return switch (condition) {
      case Condition.Comparison comparison -> {
        FieldDefinition field = requireField(comparison.field(), fields, operationIndex);
        validateComparison(field, comparison.operator(), comparison.value(), operationIndex);
        yield new CompiledCondition.Comparison(field, comparison.operator(), comparison.value());
      }
      case Condition.All all ->
          new CompiledCondition.All(compileConditions(all.conditions(), fields, operationIndex));
      case Condition.Any any ->
          new CompiledCondition.Any(compileConditions(any.conditions(), fields, operationIndex));
      case Condition.Not not ->
          new CompiledCondition.Not(compileCondition(not.condition(), fields, operationIndex));
    };
  }

  private List<CompiledCondition> compileConditions(
      List<Condition> conditions, Map<FieldPath, FieldDefinition> fields, int operationIndex)
      throws TransformationValidationException {
    List<CompiledCondition> compiled = new ArrayList<>(conditions.size());
    for (Condition condition : conditions) {
      compiled.add(compileCondition(condition, fields, operationIndex));
    }
    return compiled;
  }

  private void validateComparison(
      FieldDefinition field, ComparisonOperator operator, TypedValue value, int operationIndex)
      throws TransformationValidationException {
    if (field.type() != value.type()) {
      throw failure(
          ValidationErrorCode.TYPE_MISMATCH,
          operationIndex,
          "comparison value type " + value.type() + " does not match " + field.type());
    }
    if (field.type() == FieldType.OBJECT) {
      throw failure(
          ValidationErrorCode.INVALID_COMPARISON,
          operationIndex,
          "objects cannot be compared: " + field.path());
    }
    if (operator != ComparisonOperator.EQ
        && operator != ComparisonOperator.NE
        && field.type() != FieldType.INT64
        && field.type() != FieldType.FIXED_DECIMAL
        && field.type() != FieldType.TIMESTAMP_NANOS
        && field.type() != FieldType.STRING) {
      throw failure(
          ValidationErrorCode.INVALID_COMPARISON,
          operationIndex,
          operator + " is not supported for " + field.type());
    }
  }

  private FieldDefinition requireField(
      FieldPath path, Map<FieldPath, FieldDefinition> fields, int operationIndex)
      throws TransformationValidationException {
    FieldDefinition field = fields.get(path);
    if (field == null) {
      throw failure(
          ValidationErrorCode.UNKNOWN_FIELD, operationIndex, "unknown field path: " + path);
    }
    return field;
  }

  private void requireWritable(
      FieldDefinition field, Map<FieldPath, FieldDefinition> fields, int operationIndex)
      throws TransformationValidationException {
    FieldPath cursor = field.path();
    while (cursor != null) {
      FieldDefinition definition = fields.get(cursor);
      if (definition != null && definition.protectedField()) {
        throw failure(
            ValidationErrorCode.PROTECTED_FIELD,
            operationIndex,
            "protected canonical field cannot be modified: " + field.path());
      }
      cursor = cursor.parent();
    }
  }

  private void requireWritableSubtree(
      FieldDefinition field, Map<FieldPath, FieldDefinition> fields, int operationIndex)
      throws TransformationValidationException {
    requireWritable(field, fields, operationIndex);
    for (FieldDefinition candidate : fields.values()) {
      if (candidate.protectedField() && candidate.path().isSameOrDescendantOf(field.path())) {
        throw failure(
            ValidationErrorCode.PROTECTED_FIELD,
            operationIndex,
            "field subtree contains protected canonical field: " + candidate.path());
      }
    }
  }

  private void requireAvailableTarget(
      FieldPath target, Map<FieldPath, FieldDefinition> fields, int operationIndex)
      throws TransformationValidationException {
    if (fields.containsKey(target)) {
      throw failure(
          ValidationErrorCode.TARGET_EXISTS,
          operationIndex,
          "target field already exists: " + target);
    }
    for (FieldDefinition definition : fields.values()) {
      if (definition.path().isSameOrDescendantOf(target)) {
        throw failure(
            ValidationErrorCode.TARGET_EXISTS,
            operationIndex,
            "target would replace an existing subtree: " + target);
      }
    }
  }

  private void requireObjectParent(
      FieldPath target, Map<FieldPath, FieldDefinition> fields, int operationIndex)
      throws TransformationValidationException {
    FieldPath parent = target.parent();
    if (parent == null) {
      return;
    }
    FieldDefinition parentDefinition = fields.get(parent);
    if (parentDefinition == null || parentDefinition.type() != FieldType.OBJECT) {
      throw failure(
          ValidationErrorCode.INVALID_TARGET,
          operationIndex,
          "target parent must exist and be OBJECT: " + target);
    }
    requireWritable(parentDefinition, fields, operationIndex);
  }

  private boolean castSupported(FieldType source, FieldType target) {
    if (source == target) {
      return true;
    }
    return switch (source) {
      case STRING ->
          target == FieldType.BOOLEAN
              || target == FieldType.INT64
              || target == FieldType.FIXED_DECIMAL
              || target == FieldType.ENUM
              || target == FieldType.TIMESTAMP_NANOS;
      case BOOLEAN -> target == FieldType.STRING;
      case INT64 -> target == FieldType.STRING || target == FieldType.FIXED_DECIMAL;
      case FIXED_DECIMAL, ENUM -> target == FieldType.STRING;
      case TIMESTAMP_NANOS -> target == FieldType.STRING || target == FieldType.INT64;
      case OBJECT -> false;
    };
  }

  private TransformationValidationException failure(
      ValidationErrorCode code, int operationIndex, String message) {
    return new TransformationValidationException(code, operationIndex, message);
  }
}
