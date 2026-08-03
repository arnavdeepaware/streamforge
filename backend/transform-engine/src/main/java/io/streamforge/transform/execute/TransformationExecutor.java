package io.streamforge.transform.execute;

import io.streamforge.common.model.CanonicalEvent;
import io.streamforge.common.model.FixedDecimal;
import io.streamforge.transform.compile.CompiledCondition;
import io.streamforge.transform.compile.CompiledOperation;
import io.streamforge.transform.compile.CompiledTransformation;
import io.streamforge.transform.compile.FieldDefinition;
import io.streamforge.transform.config.FieldPath;
import io.streamforge.transform.config.FieldType;
import io.streamforge.transform.config.TypedValue;
import java.math.BigInteger;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Deterministically executes one previously compiled transformation plan against independent
 * events.
 */
public final class TransformationExecutor {

  private final CompiledTransformation transformation;
  private final TransformationExecutionLimits limits;

  /** Creates an executor for a plan compiled once during configuration activation. */
  public TransformationExecutor(CompiledTransformation transformation) {
    this(transformation, TransformationExecutionLimits.DEFAULT);
  }

  /** Creates an executor with explicit per-event resource limits. */
  public TransformationExecutor(
      CompiledTransformation transformation, TransformationExecutionLimits limits) {
    if (transformation == null || limits == null) {
      throw new IllegalArgumentException("compiled transformation and limits must not be null");
    }
    this.transformation = transformation;
    this.limits = limits;
  }

  /**
   * Applies the already-compiled plan to one event without changing that event or any prior result.
   *
   * <p>Expected data-dependent errors are returned as {@link TransformationResult.Failed}, allowing
   * callers to continue processing later events.
   */
  public TransformationResult execute(CanonicalEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("canonical event must not be null");
    }
    if (transformation.operations().size() > limits.maxOperationCount()) {
      return failed(
          TransformationFailureCode.OPERATION_LIMIT_EXCEEDED,
          0,
          "plan",
          "$",
          "compiled operation count exceeds configured limit of " + limits.maxOperationCount());
    }

    MutableDocument document;
    try {
      document = new MutableDocument(CanonicalEventDocumentFactory.mutableDocument(event));
      enforceDocumentLimits(document, 0, "document_view", "$");
    } catch (DocumentAccessException exception) {
      return failed(
          exception.code(), 0, "document_view", exception.fieldPath(), exception.getMessage());
    } catch (RuntimeException exception) {
      return unexpected(0, "document_view", "$", exception);
    }

    for (int index = 0; index < transformation.operations().size(); index++) {
      CompiledOperation operation = transformation.operations().get(index);
      String operationName = operationName(operation);
      String fieldPath = operationPath(operation);
      try {
        if (operation instanceof CompiledOperation.Filter filter
            && !evaluate(filter.condition(), document)) {
          return new TransformationResult.Filtered(event);
        }
        if (!(operation instanceof CompiledOperation.Filter)) {
          apply(operation, document);
        }
        enforceDocumentLimits(document, index, operationName, fieldPath);
      } catch (DocumentAccessException exception) {
        return failed(
            exception.code(), index, operationName, exception.fieldPath(), exception.getMessage());
      } catch (RuntimeException exception) {
        return unexpected(index, operationName, fieldPath, exception);
      }
    }
    return new TransformationResult.Transformed(document.freeze());
  }

  private void apply(CompiledOperation operation, MutableDocument document)
      throws DocumentAccessException {
    switch (operation) {
      case CompiledOperation.Select select ->
          document.retain(
              select.fields().stream()
                  .map(FieldDefinition::path)
                  .collect(Collectors.toUnmodifiableSet()));
      case CompiledOperation.Rename rename ->
          document.rename(rename.source().path(), rename.target());
      case CompiledOperation.Remove remove -> document.remove(remove.field().path());
      case CompiledOperation.AddConstant constant ->
          document.add(constant.path(), value(constant.value()));
      case CompiledOperation.Cast cast ->
          document.replace(cast.field().path(), cast(document.required(cast.field().path()), cast));
      case CompiledOperation.ScaleFixedDecimal scale ->
          document.replace(
              scale.field().path(),
              scale(
                  document.required(scale.field().path()),
                  scale.field().path(),
                  scale.targetScale()));
      case CompiledOperation.EnumMap enumMap ->
          document.replace(
              enumMap.field().path(),
              enumMap(
                  document.required(enumMap.field().path()), enumMap.field(), enumMap.mapping()));
      case CompiledOperation.Filter ignored -> {
        // Filter evaluation is handled before mutation to return the distinct filtered result.
      }
      case CompiledOperation.CreateObject create -> document.add(create.path(), Map.of());
      case CompiledOperation.ConditionalField conditional ->
          document.add(
              conditional.path(),
              value(
                  evaluate(conditional.condition(), document)
                      ? conditional.whenTrue()
                      : conditional.whenFalse()));
    }
  }

  private boolean evaluate(CompiledCondition condition, MutableDocument document)
      throws DocumentAccessException {
    return switch (condition) {
      case CompiledCondition.Comparison comparison -> compare(comparison, document);
      case CompiledCondition.All all -> {
        for (CompiledCondition child : all.conditions()) {
          if (!evaluate(child, document)) {
            yield false;
          }
        }
        yield true;
      }
      case CompiledCondition.Any any -> {
        for (CompiledCondition child : any.conditions()) {
          if (evaluate(child, document)) {
            yield true;
          }
        }
        yield false;
      }
      case CompiledCondition.Not not -> !evaluate(not.condition(), document);
    };
  }

  private boolean compare(CompiledCondition.Comparison comparison, MutableDocument document)
      throws DocumentAccessException {
    Object left = document.required(comparison.field().path());
    validateRuntimeType(left, comparison.field());
    Object right = value(comparison.value());
    int comparisonResult =
        compareValues(left, right, comparison.field().type(), comparison.field().path());
    return switch (comparison.operator()) {
      case EQ -> comparisonResult == 0;
      case NE -> comparisonResult != 0;
      case LT -> comparisonResult < 0;
      case LTE -> comparisonResult <= 0;
      case GT -> comparisonResult > 0;
      case GTE -> comparisonResult >= 0;
    };
  }

  private Object cast(Object source, CompiledOperation.Cast operation)
      throws DocumentAccessException {
    validateRuntimeType(source, operation.field());
    FieldType sourceType = operation.field().type();
    FieldType targetType = operation.targetType();
    FieldPath path = operation.field().path();
    if (sourceType == targetType) {
      return source;
    }
    try {
      return switch (sourceType) {
        case STRING -> castString((String) source, targetType, path);
        case BOOLEAN -> source.toString();
        case INT64 ->
            targetType == FieldType.STRING ? source.toString() : new FixedDecimal((long) source, 0);
        case FIXED_DECIMAL, ENUM -> source.toString();
        case TIMESTAMP_NANOS -> targetType == FieldType.STRING ? source.toString() : source;
        case OBJECT -> throw invalidCast(path, "objects cannot be cast");
      };
    } catch (NumberFormatException | ArithmeticException exception) {
      throw invalidCast(path, "value cannot be converted exactly: " + exception.getMessage());
    }
  }

  private Object castString(String source, FieldType targetType, FieldPath path)
      throws DocumentAccessException {
    return switch (targetType) {
      case BOOLEAN -> {
        if (!source.equals("true") && !source.equals("false")) {
          throw invalidCast(path, "boolean text must be exactly true or false");
        }
        yield Boolean.parseBoolean(source);
      }
      case INT64 -> Long.parseLong(source);
      case FIXED_DECIMAL -> fixedDecimal(source, path);
      case ENUM -> {
        if (source.isBlank()) {
          throw invalidCast(path, "enum text must not be blank");
        }
        yield source;
      }
      case TIMESTAMP_NANOS -> {
        long value = Long.parseLong(source);
        if (value < 0) {
          throw invalidCast(path, "timestamp nanoseconds must be nonnegative");
        }
        yield value;
      }
      case STRING, OBJECT ->
          throw invalidCast(path, "unsupported string cast target: " + targetType);
    };
  }

  private FixedDecimal fixedDecimal(String source, FieldPath path) throws DocumentAccessException {
    if (source.isEmpty()) {
      throw invalidCast(path, "fixed decimal text must not be empty");
    }
    int position = source.charAt(0) == '-' ? 1 : 0;
    if (position == source.length()) {
      throw invalidCast(path, "fixed decimal text must contain digits");
    }
    int decimalPoint = source.indexOf('.', position);
    if (decimalPoint != -1 && source.indexOf('.', decimalPoint + 1) != -1) {
      throw invalidCast(path, "fixed decimal text must contain at most one decimal point");
    }
    String integral =
        decimalPoint == -1 ? source.substring(position) : source.substring(position, decimalPoint);
    String fractional = decimalPoint == -1 ? "" : source.substring(decimalPoint + 1);
    if (integral.isEmpty()
        || (decimalPoint != -1 && fractional.isEmpty())
        || (!fractional.isEmpty() && !allDigits(fractional))
        || !allDigits(integral)) {
      throw invalidCast(
          path, "fixed decimal text must contain digits with an optional decimal point");
    }
    if (fractional.length() > FixedDecimal.MAX_SCALE) {
      throw invalidCast(
          path, "fixed decimal scale must be between 0 and " + FixedDecimal.MAX_SCALE);
    }
    BigInteger mantissa = new BigInteger((position == 1 ? "-" : "") + integral + fractional);
    return new FixedDecimal(mantissa.longValueExact(), fractional.length());
  }

  private FixedDecimal scale(Object source, FieldPath path, int targetScale)
      throws DocumentAccessException {
    if (!(source instanceof FixedDecimal fixedDecimal)) {
      throw typeMismatch(path, FieldType.FIXED_DECIMAL, source);
    }
    try {
      int scaleDifference = targetScale - fixedDecimal.scale();
      BigInteger mantissa = BigInteger.valueOf(fixedDecimal.mantissa());
      if (scaleDifference > 0) {
        return new FixedDecimal(
            mantissa.multiply(BigInteger.TEN.pow(scaleDifference)).longValueExact(), targetScale);
      }
      if (scaleDifference < 0) {
        BigInteger divisor = BigInteger.TEN.pow(-scaleDifference);
        BigInteger[] result = mantissa.divideAndRemainder(divisor);
        if (result[1].signum() != 0) {
          throw new ArithmeticException("scale reduction would discard nonzero fractional digits");
        }
        return new FixedDecimal(result[0].longValueExact(), targetScale);
      }
      return fixedDecimal;
    } catch (ArithmeticException exception) {
      throw new DocumentAccessException(
          TransformationFailureCode.PRECISION_LOSS,
          path.toString(),
          "scale change would lose precision or overflow: " + exception.getMessage());
    }
  }

  private String enumMap(Object source, FieldDefinition field, Map<String, String> mapping)
      throws DocumentAccessException {
    validateRuntimeType(source, field);
    String mapped = mapping.get(source);
    if (mapped == null) {
      throw new DocumentAccessException(
          TransformationFailureCode.UNMAPPED_ENUM_VALUE,
          field.path().toString(),
          "enum mapping has no entry for value: " + source);
    }
    return mapped;
  }

  private int compareValues(Object left, Object right, FieldType type, FieldPath path)
      throws DocumentAccessException {
    return switch (type) {
      case STRING, ENUM -> ((String) left).compareTo((String) right);
      case BOOLEAN -> Boolean.compare((boolean) left, (boolean) right);
      case INT64, TIMESTAMP_NANOS -> Long.compare((long) left, (long) right);
      case FIXED_DECIMAL -> compareFixedDecimals((FixedDecimal) left, (FixedDecimal) right);
      case OBJECT -> throw typeMismatch(path, type, left);
    };
  }

  private Object value(TypedValue value) {
    return switch (value) {
      case TypedValue.StringValue string -> string.value();
      case TypedValue.BooleanValue bool -> bool.value();
      case TypedValue.Int64Value integer -> integer.value();
      case TypedValue.FixedDecimalValue decimal -> decimal.value();
      case TypedValue.EnumValue enumeration -> enumeration.value();
      case TypedValue.TimestampNanosValue timestamp -> timestamp.value();
    };
  }

  private int compareFixedDecimals(FixedDecimal left, FixedDecimal right) {
    int commonScale = Math.max(left.scale(), right.scale());
    BigInteger leftMantissa =
        BigInteger.valueOf(left.mantissa())
            .multiply(BigInteger.TEN.pow(commonScale - left.scale()));
    BigInteger rightMantissa =
        BigInteger.valueOf(right.mantissa())
            .multiply(BigInteger.TEN.pow(commonScale - right.scale()));
    return leftMantissa.compareTo(rightMantissa);
  }

  private boolean allDigits(String value) {
    for (int index = 0; index < value.length(); index++) {
      if (value.charAt(index) < '0' || value.charAt(index) > '9') {
        return false;
      }
    }
    return true;
  }

  private void validateRuntimeType(Object value, FieldDefinition definition)
      throws DocumentAccessException {
    boolean matches =
        switch (definition.type()) {
          case OBJECT -> value instanceof Map<?, ?>;
          case STRING, ENUM -> value instanceof String;
          case BOOLEAN -> value instanceof Boolean;
          case INT64, TIMESTAMP_NANOS -> value instanceof Long;
          case FIXED_DECIMAL -> value instanceof FixedDecimal;
        };
    if (!matches) {
      throw typeMismatch(definition.path(), definition.type(), value);
    }
  }

  private void enforceDocumentLimits(
      MutableDocument document, int operationIndex, String operationName, String fieldPath)
      throws DocumentAccessException {
    if (document.nestingDepth() > limits.maxNestingDepth()) {
      throw new DocumentAccessException(
          TransformationFailureCode.NESTING_DEPTH_EXCEEDED,
          fieldPath,
          "document nesting depth exceeds configured limit of " + limits.maxNestingDepth());
    }
    if (document.fieldCount() > limits.maxOutputFieldCount()) {
      throw new DocumentAccessException(
          TransformationFailureCode.OUTPUT_FIELD_LIMIT_EXCEEDED,
          fieldPath,
          "document field count exceeds configured limit of " + limits.maxOutputFieldCount());
    }
  }

  private TransformationResult.Failed failed(
      TransformationFailureCode code,
      int operationIndex,
      String operationName,
      String fieldPath,
      String detail) {
    return new TransformationResult.Failed(
        new TransformationFailure(code, operationIndex, operationName, fieldPath, detail));
  }

  private TransformationResult.Failed unexpected(
      int operationIndex, String operationName, String fieldPath, RuntimeException exception) {
    String detail =
        exception.getMessage() == null
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
    return failed(
        TransformationFailureCode.UNEXPECTED_RUNTIME_FAILURE,
        operationIndex,
        operationName,
        fieldPath,
        detail);
  }

  private DocumentAccessException invalidCast(FieldPath path, String detail) {
    return new DocumentAccessException(
        TransformationFailureCode.INVALID_CAST, path.toString(), detail);
  }

  private DocumentAccessException typeMismatch(FieldPath path, FieldType expected, Object actual) {
    String actualType = actual == null ? "null" : actual.getClass().getSimpleName();
    return new DocumentAccessException(
        TransformationFailureCode.TYPE_MISMATCH,
        path.toString(),
        "expected " + expected + " but found " + actualType);
  }

  private String operationName(CompiledOperation operation) {
    return switch (operation) {
      case CompiledOperation.Select ignored -> "select";
      case CompiledOperation.Rename ignored -> "rename";
      case CompiledOperation.Remove ignored -> "remove";
      case CompiledOperation.AddConstant ignored -> "add_constant";
      case CompiledOperation.Cast ignored -> "cast";
      case CompiledOperation.ScaleFixedDecimal ignored -> "scale_fixed_decimal";
      case CompiledOperation.EnumMap ignored -> "enum_map";
      case CompiledOperation.Filter ignored -> "filter";
      case CompiledOperation.CreateObject ignored -> "create_object";
      case CompiledOperation.ConditionalField ignored -> "conditional_field";
    };
  }

  private String operationPath(CompiledOperation operation) {
    return switch (operation) {
      case CompiledOperation.Select ignored -> "$";
      case CompiledOperation.Rename rename -> rename.source().path().toString();
      case CompiledOperation.Remove remove -> remove.field().path().toString();
      case CompiledOperation.AddConstant constant -> constant.path().toString();
      case CompiledOperation.Cast cast -> cast.field().path().toString();
      case CompiledOperation.ScaleFixedDecimal scale -> scale.field().path().toString();
      case CompiledOperation.EnumMap enumMap -> enumMap.field().path().toString();
      case CompiledOperation.Filter ignored -> "$";
      case CompiledOperation.CreateObject create -> create.path().toString();
      case CompiledOperation.ConditionalField conditional -> conditional.path().toString();
    };
  }
}
