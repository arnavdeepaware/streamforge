package io.streamforge.transform.blueprint;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.streamforge.common.model.FixedDecimal;
import io.streamforge.transform.config.ComparisonOperator;
import io.streamforge.transform.config.FieldPath;
import io.streamforge.transform.config.TypedValue;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict parser for closed output-blueprint JSON; no templating syntax is accepted. */
public final class OutputBlueprintParser {

  private static final int MAX_DEPTH = 16;
  private static final int MAX_FIELDS = 512;
  private static final int MAX_ARRAY_ITEMS = 256;
  private final ObjectMapper mapper;

  public OutputBlueprintParser() {
    JsonFactory factory =
        JsonFactory.builder()
            .streamReadConstraints(
                StreamReadConstraints.builder()
                    .maxNestingDepth(64)
                    .maxStringLength(100_000)
                    .maxNumberLength(128)
                    .build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    mapper =
        new ObjectMapper(factory)
            .setNodeFactory(new JsonNodeFactory(true))
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
  }

  public OutputBlueprint parse(String json) throws OutputBlueprintConfigException {
    if (json == null) {
      throw error(BlueprintConfigErrorCode.INVALID_VALUE, "$", "blueprint must not be null");
    }
    try {
      return parse(new StringReader(json));
    } catch (IOException exception) {
      throw new IllegalStateException("unexpected in-memory read failure", exception);
    }
  }

  public OutputBlueprint parse(Reader reader) throws IOException, OutputBlueprintConfigException {
    try {
      JsonNode root = mapper.readTree(reader);
      requireObject(root, "$", Set.of("schemaVersion", "output"));
      BlueprintSchemaVersion version;
      try {
        version = BlueprintSchemaVersion.parse(text(root, "schemaVersion", "$.schemaVersion"));
      } catch (IllegalArgumentException exception) {
        throw error(
            BlueprintConfigErrorCode.UNSUPPORTED_VERSION,
            "$.schemaVersion",
            exception.getMessage());
      }
      Counter counter = new Counter();
      OutputBlueprintValue output =
          value(required(root, "output", "$.output"), "$.output", 0, counter);
      if (!(output instanceof OutputBlueprintValue.ObjectValue object)) {
        throw error(
            BlueprintConfigErrorCode.INVALID_VALUE,
            "$.output",
            "blueprint output must be an object");
      }
      return new OutputBlueprint(version, object);
    } catch (JsonProcessingException exception) {
      throw error(BlueprintConfigErrorCode.MALFORMED_JSON, "$", exception.getOriginalMessage());
    }
  }

  private OutputBlueprintValue value(JsonNode node, String location, int depth, Counter counter)
      throws OutputBlueprintConfigException {
    if (depth > MAX_DEPTH) {
      throw error(
          BlueprintConfigErrorCode.LIMIT_EXCEEDED,
          location,
          "blueprint nesting exceeds " + MAX_DEPTH);
    }
    requireObjectNode(node, location);
    String kind = text(node, "kind", location + ".kind");
    try {
      return switch (kind) {
        case "reference" -> {
          requireOnly(node, location, Set.of("kind", "source", "path"));
          yield new OutputBlueprintValue.Reference(reference(node, location));
        }
        case "literal" -> {
          requireOnly(node, location, Set.of("kind", "value"));
          yield new OutputBlueprintValue.Literal(
              literal(required(node, "value", location + ".value"), location + ".value"));
        }
        case "object" -> {
          requireOnly(node, location, Set.of("kind", "fields"));
          JsonNode fields = required(node, "fields", location + ".fields");
          requireObjectNode(fields, location + ".fields");
          LinkedHashMap<String, OutputBlueprintValue> parsed = new LinkedHashMap<>();
          Iterator<Map.Entry<String, JsonNode>> iterator = fields.fields();
          while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            String fieldLocation = location + ".fields." + entry.getKey();
            if (entry.getKey().isBlank() || entry.getKey().length() > 128) {
              throw error(
                  BlueprintConfigErrorCode.INVALID_VALUE,
                  fieldLocation,
                  "output field key must be 1..128 characters");
            }
            counter.add(fieldLocation);
            parsed.put(entry.getKey(), value(entry.getValue(), fieldLocation, depth + 1, counter));
          }
          yield new OutputBlueprintValue.ObjectValue(parsed);
        }
        case "array" -> {
          requireOnly(node, location, Set.of("kind", "items"));
          JsonNode items = array(node, "items", location + ".items");
          if (items.size() > MAX_ARRAY_ITEMS) {
            throw error(
                BlueprintConfigErrorCode.LIMIT_EXCEEDED,
                location + ".items",
                "array exceeds " + MAX_ARRAY_ITEMS + " items");
          }
          List<OutputBlueprintValue> parsed = new ArrayList<>(items.size());
          for (int index = 0; index < items.size(); index++) {
            String itemLocation = location + ".items[" + index + "]";
            counter.add(itemLocation);
            parsed.add(value(items.get(index), itemLocation, depth + 1, counter));
          }
          yield new OutputBlueprintValue.ArrayValue(parsed);
        }
        case "format" -> {
          requireOnly(node, location, Set.of("kind", "source", "path", "format"));
          yield new OutputBlueprintValue.Formatted(
              reference(node, location),
              enumValue(
                  BlueprintFormat.class,
                  text(node, "format", location + ".format"),
                  location + ".format"));
        }
        case "conditional" -> {
          requireOnly(node, location, Set.of("kind", "condition", "value"));
          yield new OutputBlueprintValue.Conditional(
              condition(
                  required(node, "condition", location + ".condition"), location + ".condition", 1),
              value(
                  required(node, "value", location + ".value"),
                  location + ".value",
                  depth + 1,
                  counter));
        }
        default ->
            throw error(
                BlueprintConfigErrorCode.UNKNOWN_KIND,
                location + ".kind",
                "unknown blueprint kind: " + kind);
      };
    } catch (IllegalArgumentException exception) {
      throw error(BlueprintConfigErrorCode.INVALID_VALUE, location, exception.getMessage());
    }
  }

  private BlueprintCondition condition(JsonNode node, String location, int depth)
      throws OutputBlueprintConfigException {
    if (depth > MAX_DEPTH) {
      throw error(
          BlueprintConfigErrorCode.LIMIT_EXCEEDED,
          location,
          "condition nesting exceeds " + MAX_DEPTH);
    }
    requireObjectNode(node, location);
    String type = text(node, "type", location + ".type");
    try {
      return switch (type) {
        case "comparison" -> {
          requireOnly(node, location, Set.of("type", "source", "path", "operator", "value"));
          yield new BlueprintCondition.Comparison(
              reference(node, location),
              enumValue(
                  ComparisonOperator.class,
                  text(node, "operator", location + ".operator"),
                  location + ".operator"),
              typedValue(required(node, "value", location + ".value"), location + ".value"));
        }
        case "all", "any" -> {
          requireOnly(node, location, Set.of("type", "conditions"));
          JsonNode children = array(node, "conditions", location + ".conditions");
          if (children.isEmpty()) {
            throw error(
                BlueprintConfigErrorCode.INVALID_VALUE,
                location + ".conditions",
                "conditions must not be empty");
          }
          List<BlueprintCondition> parsed = new ArrayList<>(children.size());
          for (int index = 0; index < children.size(); index++) {
            parsed.add(
                condition(children.get(index), location + ".conditions[" + index + "]", depth + 1));
          }
          yield type.equals("all")
              ? new BlueprintCondition.All(parsed)
              : new BlueprintCondition.Any(parsed);
        }
        case "not" -> {
          requireOnly(node, location, Set.of("type", "condition"));
          yield new BlueprintCondition.Not(
              condition(
                  required(node, "condition", location + ".condition"),
                  location + ".condition",
                  depth + 1));
        }
        default ->
            throw error(
                BlueprintConfigErrorCode.UNKNOWN_CONDITION,
                location + ".type",
                "unknown condition type: " + type);
      };
    } catch (IllegalArgumentException exception) {
      throw error(BlueprintConfigErrorCode.INVALID_VALUE, location, exception.getMessage());
    }
  }

  private BlueprintReference reference(JsonNode node, String location)
      throws OutputBlueprintConfigException {
    try {
      return new BlueprintReference(
          BlueprintSource.parse(text(node, "source", location + ".source")),
          new FieldPath(text(node, "path", location + ".path")));
    } catch (IllegalArgumentException exception) {
      throw error(BlueprintConfigErrorCode.INVALID_VALUE, location, exception.getMessage());
    }
  }

  private Object literal(JsonNode node, String location) throws OutputBlueprintConfigException {
    if (node.isTextual()) return node.textValue();
    if (node.isBoolean()) return node.booleanValue();
    if (!node.isNumber()) {
      throw error(
          BlueprintConfigErrorCode.INVALID_VALUE,
          location,
          "literal must be a string, boolean, or exact number");
    }
    try {
      if (node.isIntegralNumber()) {
        if (!node.canConvertToLong())
          throw new ArithmeticException("integer is outside int64 range");
        return node.longValue();
      }
      return fixedDecimal(node.asText());
    } catch (ArithmeticException exception) {
      throw error(BlueprintConfigErrorCode.INVALID_VALUE, location, exception.getMessage());
    }
  }

  private TypedValue typedValue(JsonNode node, String location)
      throws OutputBlueprintConfigException {
    requireObjectNode(node, location);
    String type = text(node, "type", location + ".type");
    return switch (type) {
      case "STRING" -> {
        requireOnly(node, location, Set.of("type", "value"));
        yield new TypedValue.StringValue(text(node, "value", location + ".value"));
      }
      case "BOOLEAN" -> {
        requireOnly(node, location, Set.of("type", "value"));
        JsonNode value = required(node, "value", location + ".value");
        if (!value.isBoolean())
          throw error(
              BlueprintConfigErrorCode.INVALID_VALUE, location + ".value", "value must be boolean");
        yield new TypedValue.BooleanValue(value.booleanValue());
      }
      case "INT64" -> {
        requireOnly(node, location, Set.of("type", "value"));
        yield new TypedValue.Int64Value(
            integer(required(node, "value", location + ".value"), location + ".value"));
      }
      case "FIXED_DECIMAL" -> {
        requireOnly(node, location, Set.of("type", "mantissa", "scale"));
        yield new TypedValue.FixedDecimalValue(
            new FixedDecimal(
                integer(required(node, "mantissa", location + ".mantissa"), location + ".mantissa"),
                intValue(required(node, "scale", location + ".scale"), location + ".scale")));
      }
      case "ENUM" -> {
        requireOnly(node, location, Set.of("type", "value"));
        yield new TypedValue.EnumValue(text(node, "value", location + ".value"));
      }
      case "TIMESTAMP_NANOS" -> {
        requireOnly(node, location, Set.of("type", "value"));
        yield new TypedValue.TimestampNanosValue(
            integer(required(node, "value", location + ".value"), location + ".value"));
      }
      default ->
          throw error(
              BlueprintConfigErrorCode.INVALID_VALUE,
              location + ".type",
              "unsupported typed value: " + type);
    };
  }

  private FixedDecimal fixedDecimal(String input) {
    int start = input.startsWith("-") ? 1 : 0;
    int point = input.indexOf('.', start);
    String integral = point < 0 ? input.substring(start) : input.substring(start, point);
    String fractional = point < 0 ? "" : input.substring(point + 1);
    if (integral.isEmpty()
        || (point >= 0 && fractional.isEmpty())
        || !digits(integral)
        || !digits(fractional)
        || fractional.length() > FixedDecimal.MAX_SCALE) {
      throw new ArithmeticException(
          "decimal literal must be an exact fixed decimal with scale 0..18");
    }
    return new FixedDecimal(
        new BigInteger((start == 1 ? "-" : "") + integral + fractional).longValueExact(),
        fractional.length());
  }

  private boolean digits(String value) {
    for (int index = 0; index < value.length(); index++)
      if (!Character.isDigit(value.charAt(index))) return false;
    return true;
  }

  private void requireObject(JsonNode node, String location, Set<String> allowed)
      throws OutputBlueprintConfigException {
    requireObjectNode(node, location);
    requireOnly(node, location, allowed);
  }

  private void requireObjectNode(JsonNode node, String location)
      throws OutputBlueprintConfigException {
    if (node == null || !node.isObject())
      throw error(BlueprintConfigErrorCode.INVALID_VALUE, location, "value must be an object");
  }

  private void requireOnly(JsonNode node, String location, Set<String> allowed)
      throws OutputBlueprintConfigException {
    Iterator<String> fields = node.fieldNames();
    while (fields.hasNext()) {
      String field = fields.next();
      if (!allowed.contains(field))
        throw error(
            BlueprintConfigErrorCode.UNKNOWN_PROPERTY,
            location + "." + field,
            "unknown property: " + field);
    }
  }

  private JsonNode required(JsonNode node, String field, String location)
      throws OutputBlueprintConfigException {
    JsonNode value = node.get(field);
    if (value == null || value.isNull())
      throw error(
          BlueprintConfigErrorCode.MISSING_PROPERTY, location, "required property is missing");
    return value;
  }

  private JsonNode array(JsonNode node, String field, String location)
      throws OutputBlueprintConfigException {
    JsonNode value = required(node, field, location);
    if (!value.isArray())
      throw error(BlueprintConfigErrorCode.INVALID_VALUE, location, "value must be an array");
    return value;
  }

  private String text(JsonNode node, String field, String location)
      throws OutputBlueprintConfigException {
    JsonNode value = required(node, field, location);
    if (!value.isTextual() || value.textValue().isBlank())
      throw error(
          BlueprintConfigErrorCode.INVALID_VALUE, location, "value must be a non-blank string");
    return value.textValue();
  }

  private long integer(JsonNode node, String location) throws OutputBlueprintConfigException {
    if (!node.isIntegralNumber() || !node.canConvertToLong())
      throw error(BlueprintConfigErrorCode.INVALID_VALUE, location, "value must be an exact int64");
    return node.longValue();
  }

  private int intValue(JsonNode node, String location) throws OutputBlueprintConfigException {
    if (!node.isIntegralNumber() || !node.canConvertToInt())
      throw error(BlueprintConfigErrorCode.INVALID_VALUE, location, "value must be an exact int32");
    return node.intValue();
  }

  private <E extends Enum<E>> E enumValue(Class<E> type, String value, String location)
      throws OutputBlueprintConfigException {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException exception) {
      throw error(BlueprintConfigErrorCode.INVALID_VALUE, location, "unsupported value: " + value);
    }
  }

  private static OutputBlueprintConfigException error(
      BlueprintConfigErrorCode code, String location, String detail) {
    return new OutputBlueprintConfigException(code, location, detail);
  }

  private static final class Counter {
    private int fields;

    private void add(String location) throws OutputBlueprintConfigException {
      if (++fields > MAX_FIELDS)
        throw error(
            BlueprintConfigErrorCode.LIMIT_EXCEEDED,
            location,
            "blueprint fields exceed " + MAX_FIELDS);
    }
  }
}
