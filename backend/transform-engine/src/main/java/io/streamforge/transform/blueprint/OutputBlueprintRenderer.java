package io.streamforge.transform.blueprint;

import io.streamforge.common.model.FixedDecimal;
import io.streamforge.transform.config.FieldType;
import io.streamforge.transform.config.TypedValue;
import io.streamforge.transform.execute.CanonicalEventDocument;
import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Renders only compiled blueprint nodes into an immutable, JSON-shaped document. */
final class OutputBlueprintRenderer {
  BlueprintPreviewResult render(
      CompiledOutputBlueprint blueprint,
      CanonicalEventDocument canonical,
      Optional<CanonicalEventDocument> transformed) {
    try {
      Counter counter = new Counter(blueprint.limits());
      Object value = value(blueprint.output(), "$", canonical, transformed, 0, counter);
      return new BlueprintPreviewResult.Rendered(new OutputBlueprintDocument(castObject(value)));
    } catch (RenderException exception) {
      return new BlueprintPreviewResult.Failed(
          new BlueprintPreviewFailure(exception.location, exception.getMessage()));
    } catch (RuntimeException exception) {
      return new BlueprintPreviewResult.Failed(
          new BlueprintPreviewFailure(
              "$",
              exception.getMessage() == null
                  ? exception.getClass().getSimpleName()
                  : exception.getMessage()));
    }
  }

  private Object value(
      CompiledOutputBlueprintValue value,
      String location,
      CanonicalEventDocument canonical,
      Optional<CanonicalEventDocument> transformed,
      int depth,
      Counter counter)
      throws RenderException {
    if (depth > counter.limits.maxDepth())
      throw new RenderException(location, "output depth exceeds configured limit");
    return switch (value) {
      case CompiledOutputBlueprintValue.Reference reference ->
          reference(reference.reference(), canonical, transformed, location);
      case CompiledOutputBlueprintValue.Literal literal -> literal.value();
      case CompiledOutputBlueprintValue.Formatted formatted ->
          format(formatted, canonical, transformed, location);
      case CompiledOutputBlueprintValue.Conditional conditional ->
          evaluate(conditional.condition(), canonical, transformed)
              ? value(
                  conditional.value(),
                  location + ".value",
                  canonical,
                  transformed,
                  depth + 1,
                  counter)
              : Omitted.VALUE;
      case CompiledOutputBlueprintValue.ObjectValue object ->
          object(object, location, canonical, transformed, depth, counter);
      case CompiledOutputBlueprintValue.ArrayValue array ->
          array(array, location, canonical, transformed, depth, counter);
    };
  }

  private Map<String, Object> object(
      CompiledOutputBlueprintValue.ObjectValue object,
      String location,
      CanonicalEventDocument canonical,
      Optional<CanonicalEventDocument> transformed,
      int depth,
      Counter counter)
      throws RenderException {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, CompiledOutputBlueprintValue> entry : object.fields().entrySet()) {
      String child = location + "." + entry.getKey();
      Object value = value(entry.getValue(), child, canonical, transformed, depth + 1, counter);
      if (value != Omitted.VALUE) {
        counter.add(child);
        result.put(entry.getKey(), value);
      }
    }
    return result;
  }

  private List<Object> array(
      CompiledOutputBlueprintValue.ArrayValue array,
      String location,
      CanonicalEventDocument canonical,
      Optional<CanonicalEventDocument> transformed,
      int depth,
      Counter counter)
      throws RenderException {
    List<Object> result = new ArrayList<>();
    for (int index = 0; index < array.items().size(); index++) {
      String child = location + "[" + index + "]";
      Object value =
          value(array.items().get(index), child, canonical, transformed, depth + 1, counter);
      if (value != Omitted.VALUE) {
        counter.add(child);
        result.add(value);
      }
    }
    return result;
  }

  private Object format(
      CompiledOutputBlueprintValue.Formatted formatted,
      CanonicalEventDocument canonical,
      Optional<CanonicalEventDocument> transformed,
      String location)
      throws RenderException {
    Object value = reference(formatted.reference(), canonical, transformed, location);
    return switch (formatted.format()) {
      case FIXED_DECIMAL_PLAIN -> ((FixedDecimal) value).toString();
      case TIMESTAMP_ISO_UTC -> {
        long nanos = (long) value;
        yield DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L));
      }
    };
  }

  private Object reference(
      CompiledBlueprintReference reference,
      CanonicalEventDocument canonical,
      Optional<CanonicalEventDocument> transformed,
      String location)
      throws RenderException {
    CanonicalEventDocument source;
    if (reference.source() == BlueprintSource.CANONICAL) {
      source = canonical;
    } else if (transformed.isPresent()) {
      source = transformed.get();
    } else {
      throw new RenderException(location, "transformed document is required");
    }
    Optional<Object> value = source.valueAt(reference.field().path());
    if (value.isEmpty())
      throw new RenderException(
          location,
          "referenced "
              + reference.source().externalValue()
              + " field is absent: "
              + reference.field().path());
    return value.get();
  }

  private boolean evaluate(
      CompiledBlueprintCondition condition,
      CanonicalEventDocument canonical,
      Optional<CanonicalEventDocument> transformed)
      throws RenderException {
    return switch (condition) {
      case CompiledBlueprintCondition.Comparison comparison ->
          compare(comparison, canonical, transformed);
      case CompiledBlueprintCondition.All all -> {
        for (CompiledBlueprintCondition child : all.conditions())
          if (!evaluate(child, canonical, transformed)) yield false;
        yield true;
      }
      case CompiledBlueprintCondition.Any any -> {
        for (CompiledBlueprintCondition child : any.conditions())
          if (evaluate(child, canonical, transformed)) yield true;
        yield false;
      }
      case CompiledBlueprintCondition.Not not -> !evaluate(not.condition(), canonical, transformed);
    };
  }

  private boolean compare(
      CompiledBlueprintCondition.Comparison comparison,
      CanonicalEventDocument canonical,
      Optional<CanonicalEventDocument> transformed)
      throws RenderException {
    Object left =
        reference(
            comparison.reference(),
            canonical,
            transformed,
            comparison.reference().field().path().toString());
    Object right = typedValue(comparison.value());
    int result = compare(left, right, comparison.reference().field().type());
    return switch (comparison.operator()) {
      case EQ -> result == 0;
      case NE -> result != 0;
      case LT -> result < 0;
      case LTE -> result <= 0;
      case GT -> result > 0;
      case GTE -> result >= 0;
    };
  }

  private int compare(Object left, Object right, FieldType type) {
    return switch (type) {
      case STRING, ENUM -> ((String) left).compareTo((String) right);
      case BOOLEAN -> Boolean.compare((boolean) left, (boolean) right);
      case INT64, TIMESTAMP_NANOS -> Long.compare((long) left, (long) right);
      case FIXED_DECIMAL -> fixed((FixedDecimal) left, (FixedDecimal) right);
      case OBJECT -> throw new IllegalArgumentException("objects cannot be compared");
    };
  }

  private int fixed(FixedDecimal left, FixedDecimal right) {
    int scale = Math.max(left.scale(), right.scale());
    return BigInteger.valueOf(left.mantissa())
        .multiply(BigInteger.TEN.pow(scale - left.scale()))
        .compareTo(
            BigInteger.valueOf(right.mantissa())
                .multiply(BigInteger.TEN.pow(scale - right.scale())));
  }

  private Object typedValue(TypedValue value) {
    return switch (value) {
      case TypedValue.StringValue text -> text.value();
      case TypedValue.BooleanValue bool -> bool.value();
      case TypedValue.Int64Value integer -> integer.value();
      case TypedValue.FixedDecimalValue decimal -> decimal.value();
      case TypedValue.EnumValue enumeration -> enumeration.value();
      case TypedValue.TimestampNanosValue timestamp -> timestamp.value();
    };
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> castObject(Object value) {
    return (Map<String, Object>) value;
  }

  private enum Omitted {
    VALUE
  }

  private static final class Counter {
    private final OutputBlueprintLimits limits;
    private int fields;

    private Counter(OutputBlueprintLimits limits) {
      this.limits = limits;
    }

    private void add(String location) throws RenderException {
      if (++fields > limits.maxFieldCount())
        throw new RenderException(location, "output fields exceed configured limit");
    }
  }

  private static final class RenderException extends Exception {
    private final String location;

    private RenderException(String location, String detail) {
      super(detail);
      this.location = location;
    }
  }
}
