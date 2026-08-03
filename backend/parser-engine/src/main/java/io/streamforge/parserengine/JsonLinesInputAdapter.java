package io.streamforge.parserengine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.streamforge.common.model.CanonicalEvent;
import io.streamforge.common.model.CanonicalSchemaVersion;
import io.streamforge.common.model.EventId;
import io.streamforge.common.model.EventMetadata;
import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentReference;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.MarketEvent;
import io.streamforge.common.model.OrderAdded;
import io.streamforge.common.model.OrderCancelled;
import io.streamforge.common.model.OrderExecuted;
import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.Quantity;
import io.streamforge.common.model.RawEventReference;
import io.streamforge.common.model.SequenceNumber;
import io.streamforge.common.model.Side;
import io.streamforge.common.model.SourceIdentity;
import io.streamforge.common.model.Trade;
import io.streamforge.common.model.TradeId;
import io.streamforge.common.model.Venue;
import io.streamforge.parserengine.jsonl.dto.CanonicalEventDto;
import io.streamforge.parserengine.jsonl.dto.CanonicalSchemaVersionDto;
import io.streamforge.parserengine.jsonl.dto.EventMetadataDto;
import io.streamforge.parserengine.jsonl.dto.FixedDecimalDto;
import io.streamforge.parserengine.jsonl.dto.InstrumentReferenceDto;
import io.streamforge.parserengine.jsonl.dto.OrderAddedDto;
import io.streamforge.parserengine.jsonl.dto.OrderCancelledDto;
import io.streamforge.parserengine.jsonl.dto.OrderExecutedDto;
import io.streamforge.parserengine.jsonl.dto.QuoteDto;
import io.streamforge.parserengine.jsonl.dto.QuoteLevelDto;
import io.streamforge.parserengine.jsonl.dto.TradeDto;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Streaming adapter for the canonical JSON Lines representation.
 *
 * <p>The reader is consumed one line at a time and is never closed by this adapter. Numeric JSON
 * tokens are validated as exact integral values before conversion to Java {@code long}; no
 * JavaScript-style number representation is involved.
 */
public final class JsonLinesInputAdapter {

  private final ObjectMapper mapper;

  /** Creates an adapter with strict unknown-field, scalar-coercion, and trailing-token settings. */
  public JsonLinesInputAdapter() {
    mapper =
        new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
  }

  /**
   * Reads records incrementally and emits one result for each non-consumed line.
   *
   * @param input source reader owned by the caller
   * @param mode fail-fast or continue-with-errors behavior
   * @param eventConsumer receives events in source order
   * @return counts for lines read and emitted outcomes
   * @throws IOException if reading the source fails
   */
  public JsonLinesProcessingResult process(
      Reader input, JsonLinesMode mode, Consumer<JsonLinesEvent> eventConsumer) throws IOException {
    if (input == null || mode == null || eventConsumer == null) {
      throw new IllegalArgumentException("input, mode, and event consumer must not be null");
    }

    long linesRead = 0;
    long eventsProduced = 0;
    long errorsReported = 0;
    BufferedReader reader =
        input instanceof BufferedReader buffered ? buffered : new BufferedReader(input);
    String line;
    while ((line = reader.readLine()) != null) {
      linesRead++;
      JsonLinesEvent result = parseLine(linesRead, line);
      eventConsumer.accept(result);
      if (result instanceof JsonLinesCanonicalEvent) {
        eventsProduced++;
      } else {
        errorsReported++;
        if (mode == JsonLinesMode.FAIL_FAST) {
          break;
        }
      }
    }
    return new JsonLinesProcessingResult(linesRead, eventsProduced, errorsReported);
  }

  private JsonLinesEvent parseLine(long lineNumber, String line) {
    if (line.isBlank()) {
      return error(lineNumber, line, JsonLinesErrorReason.EMPTY_LINE, "line is empty");
    }
    try {
      JsonNode root = mapper.readTree(line);
      requireObject(root, "record");
      CanonicalEventDto dto = mapper.treeToValue(root, CanonicalEventDto.class);
      return new JsonLinesCanonicalEvent(lineNumber, toCanonical(dto, root), line);
    } catch (LineValidationException error) {
      return error(lineNumber, line, error.reason, error.getMessage());
    } catch (JsonProcessingException error) {
      return error(lineNumber, line, JsonLinesErrorReason.MALFORMED_JSON, detail(error));
    } catch (IllegalArgumentException error) {
      return error(lineNumber, line, JsonLinesErrorReason.NORMALIZATION_ERROR, detail(error));
    }
  }

  private CanonicalEvent toCanonical(CanonicalEventDto dto, JsonNode root) {
    EventMetadataDto metadataDto = requiredDto(dto.metadata(), "metadata");
    CanonicalSchemaVersionDto versionDto =
        requiredDto(metadataDto.schemaVersion(), "metadata.schemaVersion");
    int major = exactInt(versionDto.major(), "metadata.schemaVersion.major");
    int minor = exactInt(versionDto.minor(), "metadata.schemaVersion.minor");
    if (major != 1 || minor != 0) {
      throw invalid(JsonLinesErrorReason.SCHEMA_VERSION, "metadata.schemaVersion must be 1.0");
    }

    requireText(metadataDto.eventId(), "metadata.eventId");
    requireText(metadataDto.source(), "metadata.source");
    requireText(metadataDto.venue(), "metadata.venue");
    requireLong(metadataDto.exchangeTimestamp(), "metadata.exchangeTimestamp", 0, Long.MAX_VALUE);
    JsonNode metadataNode = root.get("metadata");
    if (metadataNode.has("receiveTimestamp")) {
      requireLong(metadataDto.receiveTimestamp(), "metadata.receiveTimestamp", 0, Long.MAX_VALUE);
    }
    requireLong(metadataDto.sequenceNumber(), "metadata.sequenceNumber", 1, Long.MAX_VALUE);
    requireText(metadataDto.rawEventReference(), "metadata.rawEventReference");

    InstrumentReferenceDto instrumentDto = requiredDto(dto.instrument(), "instrument");
    requireText(instrumentDto.symbol(), "instrument.symbol");

    SourceIdentity source =
        construct(() -> new SourceIdentity(metadataDto.source()), "metadata.source");
    SequenceNumber sequence = new SequenceNumber(metadataDto.sequenceNumber());
    EventId eventId = construct(() -> new EventId(metadataDto.eventId()), "metadata.eventId");
    if (!eventId.equals(EventId.deterministic(source, sequence))) {
      throw invalid(
          JsonLinesErrorReason.EVENT_ID_MISMATCH,
          "metadata.eventId does not match metadata.source and metadata.sequenceNumber");
    }
    Venue venue = construct(() -> new Venue(metadataDto.venue()), "metadata.venue");
    EventTimestamp exchangeTimestamp = new EventTimestamp(metadataDto.exchangeTimestamp());
    Optional<EventTimestamp> receiveTimestamp =
        Optional.ofNullable(metadataDto.receiveTimestamp()).map(EventTimestamp::new);
    RawEventReference rawReference =
        construct(
            () -> new RawEventReference(metadataDto.rawEventReference()),
            "metadata.rawEventReference");
    EventMetadata metadata =
        new EventMetadata(
            eventId,
            new CanonicalSchemaVersion(major, minor),
            source,
            venue,
            exchangeTimestamp,
            receiveTimestamp,
            sequence,
            rawReference);

    InstrumentReference instrument =
        construct(
            () -> new InstrumentReference(new InstrumentSymbol(instrumentDto.symbol())),
            "instrument.symbol");
    MarketEvent payload = toPayload(dto.payload());
    return new CanonicalEvent(metadata, instrument, payload);
  }

  private MarketEvent toPayload(JsonNode payloadNode) {
    requireObject(payloadNode, "payload");
    String type = requiredText(payloadNode, "type", "payload.type");
    return switch (type) {
      case "ORDER_ADDED" -> toOrderAdded(payloadNode);
      case "ORDER_EXECUTED" -> toOrderExecuted(payloadNode);
      case "ORDER_CANCELLED" -> toOrderCancelled(payloadNode);
      case "TRADE" -> toTrade(payloadNode);
      case "QUOTE" -> toQuote(payloadNode);
      default ->
          throw invalid(
              JsonLinesErrorReason.UNSUPPORTED_EVENT_TYPE,
              "payload.type is not supported: " + type);
    };
  }

  private OrderAdded toOrderAdded(JsonNode node) {
    validateLongNode(node, "orderId", "payload.orderId", 0, Long.MAX_VALUE);
    validateTextNode(node, "side", "payload.side");
    validateLongNode(node, "quantity", "payload.quantity", 1, Quantity.MAX_VALUE);
    validateFixedDecimalNode(node, "price", "payload.price");
    OrderAddedDto dto = tree(node, OrderAddedDto.class);
    return new OrderAdded(
        new OrderId(dto.orderId()),
        side(dto.side(), "payload.side"),
        new Quantity(dto.quantity()),
        fixedDecimal(dto.price(), "payload.price"));
  }

  private OrderExecuted toOrderExecuted(JsonNode node) {
    validateLongNode(node, "orderId", "payload.orderId", 0, Long.MAX_VALUE);
    validateLongNode(node, "executedQuantity", "payload.executedQuantity", 1, Quantity.MAX_VALUE);
    OrderExecutedDto dto = tree(node, OrderExecutedDto.class);
    return new OrderExecuted(new OrderId(dto.orderId()), new Quantity(dto.executedQuantity()));
  }

  private OrderCancelled toOrderCancelled(JsonNode node) {
    validateLongNode(node, "orderId", "payload.orderId", 0, Long.MAX_VALUE);
    validateLongNode(node, "cancelledQuantity", "payload.cancelledQuantity", 1, Quantity.MAX_VALUE);
    OrderCancelledDto dto = tree(node, OrderCancelledDto.class);
    return new OrderCancelled(new OrderId(dto.orderId()), new Quantity(dto.cancelledQuantity()));
  }

  private Trade toTrade(JsonNode node) {
    validateLongNode(node, "tradeId", "payload.tradeId", 0, Long.MAX_VALUE);
    if (node.has("aggressorSide")) {
      validateTextNode(node, "aggressorSide", "payload.aggressorSide");
    }
    validateLongNode(node, "quantity", "payload.quantity", 1, Quantity.MAX_VALUE);
    validateFixedDecimalNode(node, "price", "payload.price");
    TradeDto dto = tree(node, TradeDto.class);
    return new Trade(
        new TradeId(dto.tradeId()),
        Optional.ofNullable(dto.aggressorSide()).map(value -> side(value, "payload.aggressorSide")),
        new Quantity(dto.quantity()),
        fixedDecimal(dto.price(), "payload.price"));
  }

  private io.streamforge.common.model.Quote toQuote(JsonNode node) {
    QuoteDto dto = tree(node, QuoteDto.class);
    Optional<io.streamforge.common.model.QuoteLevel> bid =
        node.has("bid") ? Optional.of(quoteLevel(dto.bid(), "payload.bid")) : Optional.empty();
    Optional<io.streamforge.common.model.QuoteLevel> ask =
        node.has("ask") ? Optional.of(quoteLevel(dto.ask(), "payload.ask")) : Optional.empty();
    if (bid.isEmpty() && ask.isEmpty()) {
      throw invalid(JsonLinesErrorReason.REQUIRED_FIELD, "payload.bid or payload.ask is required");
    }
    return new io.streamforge.common.model.Quote(bid, ask);
  }

  private io.streamforge.common.model.QuoteLevel quoteLevel(QuoteLevelDto dto, String field) {
    if (dto == null) {
      throw invalid(JsonLinesErrorReason.REQUIRED_FIELD, field + " is required");
    }
    FixedDecimalDto priceDto = dto.price();
    if (priceDto == null || dto.quantity() == null) {
      throw invalid(
          JsonLinesErrorReason.REQUIRED_FIELD,
          field + ".price and " + field + ".quantity are required");
    }
    return new io.streamforge.common.model.QuoteLevel(
        fixedDecimal(priceDto, field + ".price"), new Quantity(dto.quantity()));
  }

  private FixedDecimal fixedDecimal(FixedDecimalDto dto, String field) {
    if (dto == null || dto.mantissa() == null || dto.scale() == null) {
      throw invalid(
          JsonLinesErrorReason.REQUIRED_FIELD,
          field + ".mantissa and " + field + ".scale are required");
    }
    return new FixedDecimal(dto.mantissa(), dto.scale());
  }

  private Side side(String value, String field) {
    return switch (value) {
      case "BUY" -> Side.BUY;
      case "SELL" -> Side.SELL;
      default -> throw invalid(JsonLinesErrorReason.INVALID_FIELD, field + " must be BUY or SELL");
    };
  }

  private <T> T tree(JsonNode node, Class<T> type) {
    try {
      return mapper.treeToValue(node, type);
    } catch (JsonProcessingException error) {
      throw invalid(JsonLinesErrorReason.INVALID_FIELD, detail(error));
    }
  }

  private static void requireObject(JsonNode node, String field) {
    if (node == null || !node.isObject()) {
      throw invalid(JsonLinesErrorReason.INVALID_FIELD, field + " must be an object");
    }
  }

  private static void validateFixedDecimalNode(JsonNode parent, String field, String path) {
    JsonNode value = requiredNode(parent, field, path);
    requireObject(value, path);
    validateLongNode(value, "mantissa", path + ".mantissa", Long.MIN_VALUE, Long.MAX_VALUE);
    validateLongNode(value, "scale", path + ".scale", 0, FixedDecimal.MAX_SCALE);
  }

  private static void validateLongNode(
      JsonNode parent, String field, String path, long minimum, long maximum) {
    JsonNode value = requiredNode(parent, field, path);
    if (!value.isIntegralNumber() || !value.canConvertToLong()) {
      throw invalid(
          JsonLinesErrorReason.INVALID_FIELD,
          path + " must be an exact integer in the Java long range");
    }
    long number = value.longValue();
    if (number < minimum || number > maximum) {
      throw invalid(JsonLinesErrorReason.INVALID_FIELD, path + " is outside its allowed range");
    }
  }

  private static void requireLong(Long value, String field, long minimum, long maximum) {
    if (value == null) {
      throw invalid(JsonLinesErrorReason.REQUIRED_FIELD, field + " is required");
    }
    if (value < minimum || value > maximum) {
      throw invalid(JsonLinesErrorReason.INVALID_FIELD, field + " is outside its allowed range");
    }
  }

  private static int exactInt(Integer value, String field) {
    if (value == null) {
      throw invalid(JsonLinesErrorReason.REQUIRED_FIELD, field + " is required");
    }
    return value;
  }

  private static void validateTextNode(JsonNode parent, String field, String path) {
    JsonNode value = requiredNode(parent, field, path);
    if (!value.isTextual()) {
      throw invalid(JsonLinesErrorReason.INVALID_FIELD, path + " must be a string");
    }
  }

  private static String requiredText(JsonNode parent, String field, String path) {
    validateTextNode(parent, field, path);
    return parent.get(field).textValue();
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isEmpty()) {
      throw invalid(JsonLinesErrorReason.REQUIRED_FIELD, field + " is required");
    }
  }

  private static JsonNode requiredNode(JsonNode parent, String field, String path) {
    if (parent == null || !hasNonNull(parent, field)) {
      throw invalid(JsonLinesErrorReason.REQUIRED_FIELD, path + " is required");
    }
    return parent.get(field);
  }

  private static boolean hasNonNull(JsonNode parent, String field) {
    return parent != null && parent.has(field) && !parent.get(field).isNull();
  }

  private static <T> T requiredDto(T value, String field) {
    if (value == null) {
      throw invalid(JsonLinesErrorReason.REQUIRED_FIELD, field + " is required");
    }
    return value;
  }

  private static <T> T construct(java.util.function.Supplier<T> constructor, String field) {
    try {
      return constructor.get();
    } catch (IllegalArgumentException error) {
      throw invalid(JsonLinesErrorReason.INVALID_FIELD, field + ": " + detail(error));
    }
  }

  private static LineValidationException invalid(JsonLinesErrorReason reason, String detail) {
    return new LineValidationException(reason, detail);
  }

  private static JsonLinesError error(
      long lineNumber, String sourceText, JsonLinesErrorReason reason, String detail) {
    return new JsonLinesError(
        lineNumber,
        reason,
        detail == null || detail.isBlank() ? reason.name() : detail,
        sourceText);
  }

  private static String detail(Exception error) {
    return error.getMessage() == null || error.getMessage().isBlank()
        ? error.getClass().getSimpleName()
        : error.getMessage();
  }

  private static final class LineValidationException extends RuntimeException {
    private final JsonLinesErrorReason reason;

    private LineValidationException(JsonLinesErrorReason reason, String detail) {
      super(detail);
      this.reason = Objects.requireNonNull(reason);
    }
  }
}
