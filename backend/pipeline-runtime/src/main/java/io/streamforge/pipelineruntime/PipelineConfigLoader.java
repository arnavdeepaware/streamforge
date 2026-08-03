package io.streamforge.pipelineruntime;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.streamforge.common.model.Side;
import io.streamforge.common.model.SourceIdentity;
import io.streamforge.common.model.Venue;
import io.streamforge.parserengine.JsonLinesMode;
import io.streamforge.parserengine.csv.CsvAdapterConfig;
import io.streamforge.parserengine.csv.CsvMode;
import io.streamforge.parserengine.csv.CsvTimestampFormat;
import io.streamforge.pipelineruntime.deadletter.DeadLetterConfig;
import io.streamforge.pipelineruntime.deadletter.DeadLetterPolicy;
import io.streamforge.pipelineruntime.output.CsvOutputColumn;
import io.streamforge.pipelineruntime.output.CsvOutputConfig;
import io.streamforge.stp.protocol.StpProtocol;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Strict loader for a small saved local-pipeline configuration document. */
public final class PipelineConfigLoader {
  private static final String SCHEMA_VERSION = "1.0";

  private final ObjectMapper mapper;

  public PipelineConfigLoader() {
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

  /**
   * Loads a complete configuration and resolves all relative paths against its parent directory.
   */
  public PipelineRunConfig load(Path path) throws IOException, PipelineConfigurationException {
    if (path == null) {
      throw failure("$", "configuration path must not be null", null);
    }
    Path absolutePath = path.toAbsolutePath();
    Path base = absolutePath.getParent();
    try {
      JsonNode root = mapper.readTree(absolutePath.toFile());
      requireObject(
          root,
          "$",
          Set.of(
              "schemaVersion",
              "pipelineId",
              "pipelineVersion",
              "input",
              "transformation",
              "blueprint",
              "output",
              "deadLetter"));
      if (!SCHEMA_VERSION.equals(text(root, "schemaVersion", "$.schemaVersion"))) {
        throw failure("$.schemaVersion", "pipeline configuration schemaVersion must be 1.0", null);
      }
      PipelineInput input = input(required(root, "input", "$.input"), "$.input", base);
      Optional<Path> transformation =
          optionalPath(root, "transformation", "$.transformation", base);
      Optional<Path> blueprint = optionalPath(root, "blueprint", "$.blueprint", base);
      PipelineOutput output = output(required(root, "output", "$.output"), "$.output", base);
      Optional<DeadLetterConfig> deadLetter = deadLetter(root, "deadLetter", "$.deadLetter", base);
      return new PipelineRunConfig(
          input,
          transformation,
          blueprint,
          output,
          new PipelineIdentity(
              textOrDefault(root, "pipelineId", "local-pipeline", "$.pipelineId"),
              textOrDefault(root, "pipelineVersion", SCHEMA_VERSION, "$.pipelineVersion")),
          deadLetter);
    } catch (JsonProcessingException exception) {
      throw failure("$", concise(exception), exception);
    } catch (IllegalArgumentException exception) {
      throw failure("$", concise(exception), exception);
    }
  }

  /**
   * Builds an executable snapshot from persisted JSON sections using the supplied local base path.
   */
  public InMemoryPipelineRunConfig loadInMemory(
      JsonNode inputConfiguration,
      String transformationConfiguration,
      String blueprintConfiguration,
      JsonNode outputConfiguration,
      JsonNode deadLetterConfiguration,
      PipelineIdentity identity,
      Path baseDirectory)
      throws PipelineConfigurationException {
    if (identity == null || baseDirectory == null) {
      throw failure("$", "pipeline identity and base directory are required", null);
    }
    try {
      ObjectNode deadLetterRoot = mapper.createObjectNode();
      if (deadLetterConfiguration != null && !deadLetterConfiguration.isNull()) {
        deadLetterRoot.set("deadLetter", deadLetterConfiguration);
      }
      Path base = baseDirectory.toAbsolutePath();
      return new InMemoryPipelineRunConfig(
          input(inputConfiguration, "$.input", base),
          optionalConfiguration(transformationConfiguration),
          optionalConfiguration(blueprintConfiguration),
          output(outputConfiguration, "$.output", base),
          identity,
          deadLetter(deadLetterRoot, "deadLetter", "$.deadLetter", base));
    } catch (IllegalArgumentException exception) {
      throw failure("$", concise(exception), exception);
    }
  }

  private static Optional<String> optionalConfiguration(String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
  }

  private PipelineInput input(JsonNode node, String location, Path base)
      throws PipelineConfigurationException {
    requireObjectNode(node, location);
    String type = text(node, "type", location + ".type");
    return switch (type) {
      case "STP_BINARY" -> stpInput(node, location, base);
      case "JSONL" -> jsonLinesInput(node, location, base);
      case "CSV" -> csvInput(node, location, base);
      default -> throw failure(location + ".type", "unsupported input type: " + type, null);
    };
  }

  private PipelineInput.StpBinary stpInput(JsonNode node, String location, Path base)
      throws PipelineConfigurationException {
    requireOnly(node, location, Set.of("type", "path", "source", "venue", "maximumFrameSize"));
    int defaultMaximum = StpProtocol.LENGTH_FIELD_WIDTH + StpProtocol.MAX_ENCODED_LENGTH;
    return new PipelineInput.StpBinary(
        path(node, "path", location + ".path", base),
        new SourceIdentity(text(node, "source", location + ".source")),
        new Venue(text(node, "venue", location + ".venue")),
        optionalInt(node, "maximumFrameSize", location + ".maximumFrameSize")
            .orElse(defaultMaximum));
  }

  private PipelineInput.JsonLines jsonLinesInput(JsonNode node, String location, Path base)
      throws PipelineConfigurationException {
    requireOnly(node, location, Set.of("type", "path", "mode"));
    return new PipelineInput.JsonLines(
        path(node, "path", location + ".path", base),
        enumValue(
            JsonLinesMode.class,
            textOrDefault(node, "mode", "CONTINUE_WITH_ERRORS", location + ".mode"),
            location + ".mode"));
  }

  private PipelineInput.Csv csvInput(JsonNode node, String location, Path base)
      throws PipelineConfigurationException {
    requireOnly(node, location, Set.of("type", "path", "mode", "csv"));
    return new PipelineInput.Csv(
        path(node, "path", location + ".path", base),
        csvAdapter(required(node, "csv", location + ".csv"), location + ".csv"),
        enumValue(
            CsvMode.class,
            textOrDefault(node, "mode", "CONTINUE_WITH_ERRORS", location + ".mode"),
            location + ".mode"));
  }

  private PipelineOutput output(JsonNode node, String location, Path base)
      throws PipelineConfigurationException {
    requireObjectNode(node, location);
    String type = text(node, "type", location + ".type");
    return switch (type) {
      case "JSONL" -> {
        requireOnly(node, location, Set.of("type", "path"));
        yield new PipelineOutput.JsonLines(path(node, "path", location + ".path", base));
      }
      case "CSV" -> {
        requireOnly(node, location, Set.of("type", "path", "csv"));
        yield new PipelineOutput.Csv(
            path(node, "path", location + ".path", base),
            csvOutput(required(node, "csv", location + ".csv"), location + ".csv"));
      }
      default -> throw failure(location + ".type", "unsupported output type: " + type, null);
    };
  }

  private Optional<DeadLetterConfig> deadLetter(
      JsonNode root, String field, String location, Path base)
      throws PipelineConfigurationException {
    if (!root.has(field) || root.get(field).isNull()) {
      return Optional.empty();
    }
    JsonNode node = root.get(field);
    requireObject(
        node, location, Set.of("policy", "path", "includePayload", "maximumPayloadBytes"));
    DeadLetterPolicy policy =
        enumValue(
            DeadLetterPolicy.class,
            text(node, "policy", location + ".policy"),
            location + ".policy");
    Optional<Path> path = optionalPath(node, "path", location + ".path", base);
    boolean includePayload =
        boolOrDefault(node, "includePayload", false, location + ".includePayload");
    int maximumPayloadBytes =
        optionalInt(node, "maximumPayloadBytes", location + ".maximumPayloadBytes")
            .orElse(DeadLetterConfig.DEFAULT_MAXIMUM_PAYLOAD_BYTES);
    try {
      return Optional.of(new DeadLetterConfig(policy, path, includePayload, maximumPayloadBytes));
    } catch (IllegalArgumentException exception) {
      throw failure(location, concise(exception), exception);
    }
  }

  private CsvAdapterConfig csvAdapter(JsonNode node, String location)
      throws PipelineConfigurationException {
    requireObject(
        node,
        location,
        Set.of(
            "delimiter",
            "hasHeader",
            "timestampColumn",
            "timestampFormat",
            "symbolColumn",
            "venueColumn",
            "constantVenue",
            "priceMantissaColumn",
            "decimalPriceColumn",
            "priceScale",
            "quantityColumn",
            "sideColumn",
            "sideMapping",
            "source"));
    String delimiter = text(node, "delimiter", location + ".delimiter");
    if (delimiter.length() != 1) {
      throw failure(location + ".delimiter", "delimiter must contain exactly one character", null);
    }
    return new CsvAdapterConfig(
        delimiter.charAt(0),
        bool(node, "hasHeader", location + ".hasHeader"),
        text(node, "timestampColumn", location + ".timestampColumn"),
        enumValue(
            CsvTimestampFormat.class,
            text(node, "timestampFormat", location + ".timestampFormat"),
            location + ".timestampFormat"),
        text(node, "symbolColumn", location + ".symbolColumn"),
        optionalText(node, "venueColumn", location + ".venueColumn"),
        optionalText(node, "constantVenue", location + ".constantVenue").map(Venue::new),
        optionalText(node, "priceMantissaColumn", location + ".priceMantissaColumn"),
        optionalText(node, "decimalPriceColumn", location + ".decimalPriceColumn"),
        integer(required(node, "priceScale", location + ".priceScale"), location + ".priceScale"),
        text(node, "quantityColumn", location + ".quantityColumn"),
        text(node, "sideColumn", location + ".sideColumn"),
        sideMapping(
            required(node, "sideMapping", location + ".sideMapping"), location + ".sideMapping"),
        new SourceIdentity(text(node, "source", location + ".source")));
  }

  private CsvOutputConfig csvOutput(JsonNode node, String location)
      throws PipelineConfigurationException {
    requireObject(node, location, Set.of("includeHeader", "columns"));
    JsonNode columns = required(node, "columns", location + ".columns");
    if (!columns.isArray() || columns.isEmpty()) {
      throw failure(location + ".columns", "CSV output columns must be a non-empty array", null);
    }
    List<CsvOutputColumn> parsed = new ArrayList<>(columns.size());
    for (int index = 0; index < columns.size(); index++) {
      String columnLocation = location + ".columns[" + index + "]";
      JsonNode column = columns.get(index);
      requireObject(column, columnLocation, Set.of("header", "path"));
      parsed.add(
          CsvOutputColumn.of(
              text(column, "header", columnLocation + ".header"),
              text(column, "path", columnLocation + ".path")));
    }
    return new CsvOutputConfig(parsed, bool(node, "includeHeader", location + ".includeHeader"));
  }

  private Map<String, Side> sideMapping(JsonNode node, String location)
      throws PipelineConfigurationException {
    requireObjectNode(node, location);
    LinkedHashMap<String, Side> mapping = new LinkedHashMap<>();
    Iterator<Map.Entry<String, JsonNode>> entries = node.fields();
    while (entries.hasNext()) {
      Map.Entry<String, JsonNode> entry = entries.next();
      if (entry.getKey().isBlank() || !entry.getValue().isTextual()) {
        throw failure(location, "CSV side mapping keys and values must be non-blank strings", null);
      }
      mapping.put(entry.getKey(), enumValue(Side.class, entry.getValue().textValue(), location));
    }
    if (mapping.isEmpty()) {
      throw failure(location, "CSV side mapping must not be empty", null);
    }
    return Map.copyOf(mapping);
  }

  private Optional<Path> optionalPath(JsonNode node, String field, String location, Path base)
      throws PipelineConfigurationException {
    return optionalText(node, field, location).map(value -> resolve(base, value));
  }

  private Path path(JsonNode node, String field, String location, Path base)
      throws PipelineConfigurationException {
    return resolve(base, text(node, field, location));
  }

  private static Path resolve(Path base, String value) {
    Path configured = Path.of(value);
    return configured.isAbsolute() ? configured : base.resolve(configured).normalize();
  }

  private void requireObject(JsonNode node, String location, Set<String> allowed)
      throws PipelineConfigurationException {
    requireObjectNode(node, location);
    requireOnly(node, location, allowed);
  }

  private void requireObjectNode(JsonNode node, String location)
      throws PipelineConfigurationException {
    if (node == null || !node.isObject()) {
      throw failure(location, "value must be an object", null);
    }
  }

  private void requireOnly(JsonNode node, String location, Set<String> allowed)
      throws PipelineConfigurationException {
    Iterator<String> fields = node.fieldNames();
    while (fields.hasNext()) {
      String field = fields.next();
      if (!allowed.contains(field)) {
        throw failure(location + "." + field, "unknown property: " + field, null);
      }
    }
  }

  private JsonNode required(JsonNode node, String field, String location)
      throws PipelineConfigurationException {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      throw failure(location, "required property is missing", null);
    }
    return value;
  }

  private String text(JsonNode node, String field, String location)
      throws PipelineConfigurationException {
    JsonNode value = required(node, field, location);
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw failure(location, "value must be a non-blank string", null);
    }
    return value.textValue();
  }

  private String textOrDefault(JsonNode node, String field, String fallback, String location)
      throws PipelineConfigurationException {
    return node.has(field) && !node.get(field).isNull() ? text(node, field, location) : fallback;
  }

  private Optional<String> optionalText(JsonNode node, String field, String location)
      throws PipelineConfigurationException {
    if (!node.has(field) || node.get(field).isNull()) {
      return Optional.empty();
    }
    return Optional.of(text(node, field, location));
  }

  private boolean bool(JsonNode node, String field, String location)
      throws PipelineConfigurationException {
    JsonNode value = required(node, field, location);
    if (!value.isBoolean()) {
      throw failure(location, "value must be boolean", null);
    }
    return value.booleanValue();
  }

  private boolean boolOrDefault(JsonNode node, String field, boolean fallback, String location)
      throws PipelineConfigurationException {
    return node.has(field) && !node.get(field).isNull() ? bool(node, field, location) : fallback;
  }

  private Optional<Integer> optionalInt(JsonNode node, String field, String location)
      throws PipelineConfigurationException {
    if (!node.has(field) || node.get(field).isNull()) {
      return Optional.empty();
    }
    return Optional.of(integer(node.get(field), location));
  }

  private int integer(JsonNode node, String location) throws PipelineConfigurationException {
    if (!node.isIntegralNumber() || !node.canConvertToInt()) {
      throw failure(location, "value must be an exact int32", null);
    }
    return node.intValue();
  }

  private <E extends Enum<E>> E enumValue(Class<E> type, String value, String location)
      throws PipelineConfigurationException {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException exception) {
      throw failure(location, "unsupported value: " + value, exception);
    }
  }

  private static PipelineConfigurationException failure(
      String location, String detail, Throwable cause) {
    return new PipelineConfigurationException(location, detail, cause);
  }

  private static String concise(Exception exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
  }
}
