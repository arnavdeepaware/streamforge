package io.streamforge.transform.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.streamforge.common.model.FixedDecimal;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict parser for the closed transformation configuration schema. */
public final class TransformationConfigParser {

  private static final int MAX_OPERATIONS = 256;
  private static final int MAX_CONDITION_DEPTH = 16;
  private static final int MAX_CONDITION_NODES = 256;
  private static final int MAX_ENUM_ENTRIES = 1024;

  private final ObjectMapper mapper;

  public TransformationConfigParser() {
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
    mapper = new ObjectMapper(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  }

  /** Parses one complete JSON configuration document. */
  public TransformationConfig parse(String json) throws TransformationConfigException {
    if (json == null) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, "$", "configuration must not be null");
    }
    try {
      return parse(new StringReader(json));
    } catch (IOException impossibleForStringReader) {
      throw new IllegalStateException(
          "unexpected in-memory read failure", impossibleForStringReader);
    }
  }

  /** Parses one complete JSON configuration document without closing the caller-owned reader. */
  public TransformationConfig parse(Reader reader)
      throws IOException, TransformationConfigException {
    if (reader == null) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, "$", "reader must not be null");
    }
    try {
      JsonNode root = mapper.readTree(reader);
      requireObject(root, "$", Set.of("schemaVersion", "operations"));
      String versionValue = requireText(root, "schemaVersion", "$.schemaVersion");
      TransformationSchemaVersion version;
      try {
        version = TransformationSchemaVersion.fromExternalValue(versionValue);
      } catch (IllegalArgumentException exception) {
        throw error(
            ConfigurationErrorCode.UNSUPPORTED_VERSION, "$.schemaVersion", exception.getMessage());
      }
      JsonNode operationNodes = requireArray(root, "operations", "$.operations");
      if (operationNodes.isEmpty()) {
        throw error(
            ConfigurationErrorCode.INVALID_VALUE, "$.operations", "operations must not be empty");
      }
      if (operationNodes.size() > MAX_OPERATIONS) {
        throw error(
            ConfigurationErrorCode.LIMIT_EXCEEDED,
            "$.operations",
            "operations must not exceed " + MAX_OPERATIONS);
      }
      List<TransformationOperation> operations = new ArrayList<>(operationNodes.size());
      ConditionCounter conditionCounter = new ConditionCounter();
      for (int index = 0; index < operationNodes.size(); index++) {
        operations.add(
            parseOperation(
                operationNodes.get(index), "$.operations[" + index + "]", conditionCounter));
      }
      return new TransformationConfig(version, operations);
    } catch (JsonProcessingException exception) {
      throw new TransformationConfigException(
          ConfigurationErrorCode.MALFORMED_JSON, "$", conciseMessage(exception), exception);
    }
  }

  private TransformationOperation parseOperation(
      JsonNode node, String location, ConditionCounter conditionCounter)
      throws TransformationConfigException {
    requireObjectNode(node, location);
    String operation = requireText(node, "op", location + ".op");
    try {
      return switch (operation) {
        case "select" -> {
          requireOnly(node, location, Set.of("op", "fields"));
          yield new TransformationOperation.Select(
              parseFieldPaths(
                  requireArray(node, "fields", location + ".fields"), location + ".fields"));
        }
        case "rename" -> {
          requireOnly(node, location, Set.of("op", "from", "to"));
          yield new TransformationOperation.Rename(
              path(requireText(node, "from", location + ".from"), location + ".from"),
              path(requireText(node, "to", location + ".to"), location + ".to"));
        }
        case "remove" -> {
          requireOnly(node, location, Set.of("op", "path"));
          yield new TransformationOperation.Remove(
              path(requireText(node, "path", location + ".path"), location + ".path"));
        }
        case "add_constant" -> {
          requireOnly(node, location, Set.of("op", "path", "value"));
          yield new TransformationOperation.AddConstant(
              path(requireText(node, "path", location + ".path"), location + ".path"),
              parseTypedValue(required(node, "value", location + ".value"), location + ".value"));
        }
        case "cast" -> {
          requireOnly(node, location, Set.of("op", "path", "toType"));
          yield new TransformationOperation.Cast(
              path(requireText(node, "path", location + ".path"), location + ".path"),
              scalarType(requireText(node, "toType", location + ".toType"), location + ".toType"));
        }
        case "scale_fixed_decimal" -> {
          requireOnly(node, location, Set.of("op", "path", "targetScale"));
          yield new TransformationOperation.ScaleFixedDecimal(
              path(requireText(node, "path", location + ".path"), location + ".path"),
              exactInt(
                  required(node, "targetScale", location + ".targetScale"),
                  location + ".targetScale"));
        }
        case "enum_map" -> {
          requireOnly(node, location, Set.of("op", "path", "mapping"));
          yield new TransformationOperation.EnumMap(
              path(requireText(node, "path", location + ".path"), location + ".path"),
              parseMapping(
                  required(node, "mapping", location + ".mapping"), location + ".mapping"));
        }
        case "filter" -> {
          requireOnly(node, location, Set.of("op", "condition"));
          yield new TransformationOperation.Filter(
              parseCondition(
                  required(node, "condition", location + ".condition"),
                  location + ".condition",
                  1,
                  conditionCounter));
        }
        case "create_object" -> {
          requireOnly(node, location, Set.of("op", "path"));
          yield new TransformationOperation.CreateObject(
              path(requireText(node, "path", location + ".path"), location + ".path"));
        }
        case "conditional_field" -> {
          requireOnly(node, location, Set.of("op", "path", "condition", "whenTrue", "whenFalse"));
          yield new TransformationOperation.ConditionalField(
              path(requireText(node, "path", location + ".path"), location + ".path"),
              parseCondition(
                  required(node, "condition", location + ".condition"),
                  location + ".condition",
                  1,
                  conditionCounter),
              parseTypedValue(
                  required(node, "whenTrue", location + ".whenTrue"), location + ".whenTrue"),
              parseTypedValue(
                  required(node, "whenFalse", location + ".whenFalse"), location + ".whenFalse"));
        }
        default ->
            throw error(
                ConfigurationErrorCode.UNKNOWN_OPERATION,
                location + ".op",
                "unknown operation: " + operation);
      };
    } catch (IllegalArgumentException exception) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, location, exception.getMessage());
    }
  }

  private Condition parseCondition(
      JsonNode node, String location, int depth, ConditionCounter counter)
      throws TransformationConfigException {
    if (depth > MAX_CONDITION_DEPTH) {
      throw error(
          ConfigurationErrorCode.LIMIT_EXCEEDED,
          location,
          "condition depth must not exceed " + MAX_CONDITION_DEPTH);
    }
    counter.increment(location);
    requireObjectNode(node, location);
    String type = requireText(node, "type", location + ".type");
    try {
      return switch (type) {
        case "comparison" -> {
          requireOnly(node, location, Set.of("type", "field", "operator", "value"));
          yield new Condition.Comparison(
              path(requireText(node, "field", location + ".field"), location + ".field"),
              enumValue(
                  ComparisonOperator.class,
                  requireText(node, "operator", location + ".operator"),
                  location + ".operator"),
              parseTypedValue(required(node, "value", location + ".value"), location + ".value"));
        }
        case "all", "any" -> {
          requireOnly(node, location, Set.of("type", "conditions"));
          JsonNode children = requireArray(node, "conditions", location + ".conditions");
          if (children.isEmpty()) {
            throw error(
                ConfigurationErrorCode.INVALID_VALUE,
                location + ".conditions",
                "condition list must not be empty");
          }
          List<Condition> conditions = new ArrayList<>(children.size());
          for (int index = 0; index < children.size(); index++) {
            conditions.add(
                parseCondition(
                    children.get(index),
                    location + ".conditions[" + index + "]",
                    depth + 1,
                    counter));
          }
          yield type.equals("all") ? new Condition.All(conditions) : new Condition.Any(conditions);
        }
        case "not" -> {
          requireOnly(node, location, Set.of("type", "condition"));
          yield new Condition.Not(
              parseCondition(
                  required(node, "condition", location + ".condition"),
                  location + ".condition",
                  depth + 1,
                  counter));
        }
        default ->
            throw error(
                ConfigurationErrorCode.UNKNOWN_CONDITION,
                location + ".type",
                "unknown condition type: " + type);
      };
    } catch (IllegalArgumentException exception) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, location, exception.getMessage());
    }
  }

  private TypedValue parseTypedValue(JsonNode node, String location)
      throws TransformationConfigException {
    requireObjectNode(node, location);
    String type = requireText(node, "type", location + ".type");
    try {
      return switch (type) {
        case "STRING" -> {
          requireOnly(node, location, Set.of("type", "value"));
          yield new TypedValue.StringValue(requireString(node, "value", location + ".value"));
        }
        case "BOOLEAN" -> {
          requireOnly(node, location, Set.of("type", "value"));
          JsonNode value = required(node, "value", location + ".value");
          if (!value.isBoolean()) {
            throw error(
                ConfigurationErrorCode.INVALID_VALUE,
                location + ".value",
                "value must be a boolean");
          }
          yield new TypedValue.BooleanValue(value.booleanValue());
        }
        case "INT64" -> {
          requireOnly(node, location, Set.of("type", "value"));
          yield new TypedValue.Int64Value(
              exactLong(required(node, "value", location + ".value"), location + ".value"));
        }
        case "FIXED_DECIMAL" -> {
          requireOnly(node, location, Set.of("type", "mantissa", "scale"));
          yield new TypedValue.FixedDecimalValue(
              new FixedDecimal(
                  exactLong(
                      required(node, "mantissa", location + ".mantissa"), location + ".mantissa"),
                  exactInt(required(node, "scale", location + ".scale"), location + ".scale")));
        }
        case "ENUM" -> {
          requireOnly(node, location, Set.of("type", "value"));
          yield new TypedValue.EnumValue(requireText(node, "value", location + ".value"));
        }
        case "TIMESTAMP_NANOS" -> {
          requireOnly(node, location, Set.of("type", "value"));
          yield new TypedValue.TimestampNanosValue(
              exactLong(required(node, "value", location + ".value"), location + ".value"));
        }
        default ->
            throw error(
                ConfigurationErrorCode.INVALID_VALUE,
                location + ".type",
                "unsupported value type: " + type);
      };
    } catch (IllegalArgumentException exception) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, location, exception.getMessage());
    }
  }

  private List<FieldPath> parseFieldPaths(JsonNode node, String location)
      throws TransformationConfigException {
    if (node.isEmpty()) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, location, "fields must not be empty");
    }
    List<FieldPath> fields = new ArrayList<>(node.size());
    for (int index = 0; index < node.size(); index++) {
      String itemLocation = location + "[" + index + "]";
      JsonNode item = node.get(index);
      if (!item.isTextual()) {
        throw error(ConfigurationErrorCode.INVALID_VALUE, itemLocation, "field must be a string");
      }
      fields.add(path(item.textValue(), itemLocation));
    }
    return fields;
  }

  private Map<String, String> parseMapping(JsonNode node, String location)
      throws TransformationConfigException {
    requireObjectNode(node, location);
    if (node.isEmpty()) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, location, "mapping must not be empty");
    }
    if (node.size() > MAX_ENUM_ENTRIES) {
      throw error(
          ConfigurationErrorCode.LIMIT_EXCEEDED,
          location,
          "mapping must not exceed " + MAX_ENUM_ENTRIES + " entries");
    }
    LinkedHashMap<String, String> mapping = new LinkedHashMap<>();
    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      if (field.getKey().isBlank()
          || !field.getValue().isTextual()
          || field.getValue().textValue().isBlank()) {
        throw error(
            ConfigurationErrorCode.INVALID_VALUE,
            location + "." + field.getKey(),
            "enum keys and mapped values must be non-blank strings");
      }
      mapping.put(field.getKey(), field.getValue().textValue());
    }
    return mapping;
  }

  private FieldPath path(String value, String location) throws TransformationConfigException {
    try {
      return new FieldPath(value);
    } catch (IllegalArgumentException exception) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, location, exception.getMessage());
    }
  }

  private FieldType scalarType(String value, String location) throws TransformationConfigException {
    FieldType type = enumValue(FieldType.class, value, location);
    if (type == FieldType.OBJECT) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, location, "OBJECT is not a cast target");
    }
    return type;
  }

  private <E extends Enum<E>> E enumValue(Class<E> type, String value, String location)
      throws TransformationConfigException {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException exception) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, location, "unsupported value: " + value);
    }
  }

  private void requireObject(JsonNode node, String location, Set<String> fields)
      throws TransformationConfigException {
    requireObjectNode(node, location);
    requireOnly(node, location, fields);
  }

  private void requireObjectNode(JsonNode node, String location)
      throws TransformationConfigException {
    if (node == null || !node.isObject()) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, location, "value must be an object");
    }
  }

  private void requireOnly(JsonNode node, String location, Set<String> allowed)
      throws TransformationConfigException {
    Iterator<String> fields = node.fieldNames();
    while (fields.hasNext()) {
      String field = fields.next();
      if (!allowed.contains(field)) {
        throw error(
            ConfigurationErrorCode.UNKNOWN_PROPERTY,
            location + "." + field,
            "unknown property: " + field);
      }
    }
  }

  private JsonNode required(JsonNode node, String field, String location)
      throws TransformationConfigException {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      throw error(
          ConfigurationErrorCode.MISSING_PROPERTY, location, "required property is missing");
    }
    return value;
  }

  private String requireText(JsonNode node, String field, String location)
      throws TransformationConfigException {
    JsonNode value = required(node, field, location);
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw error(
          ConfigurationErrorCode.INVALID_VALUE, location, "value must be a non-blank string");
    }
    return value.textValue();
  }

  private String requireString(JsonNode node, String field, String location)
      throws TransformationConfigException {
    JsonNode value = required(node, field, location);
    if (!value.isTextual()) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, location, "value must be a string");
    }
    return value.textValue();
  }

  private JsonNode requireArray(JsonNode node, String field, String location)
      throws TransformationConfigException {
    JsonNode value = required(node, field, location);
    if (!value.isArray()) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, location, "value must be an array");
    }
    return value;
  }

  private long exactLong(JsonNode node, String location) throws TransformationConfigException {
    if (!node.isIntegralNumber() || !node.canConvertToLong()) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, location, "value must be an exact int64");
    }
    return node.longValue();
  }

  private int exactInt(JsonNode node, String location) throws TransformationConfigException {
    if (!node.isIntegralNumber() || !node.canConvertToInt()) {
      throw error(ConfigurationErrorCode.INVALID_VALUE, location, "value must be an exact int32");
    }
    return node.intValue();
  }

  private static String conciseMessage(JsonProcessingException exception) {
    return exception.getOriginalMessage() == null
        ? "malformed JSON"
        : exception.getOriginalMessage();
  }

  private static TransformationConfigException error(
      ConfigurationErrorCode code, String location, String message) {
    return new TransformationConfigException(code, location, message);
  }

  private static final class ConditionCounter {
    private int count;

    private void increment(String location) throws TransformationConfigException {
      count++;
      if (count > MAX_CONDITION_NODES) {
        throw error(
            ConfigurationErrorCode.LIMIT_EXCEEDED,
            location,
            "condition nodes must not exceed " + MAX_CONDITION_NODES);
      }
    }
  }
}
