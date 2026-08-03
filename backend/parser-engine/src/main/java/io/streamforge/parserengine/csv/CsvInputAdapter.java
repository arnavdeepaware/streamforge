package io.streamforge.parserengine.csv;

import io.streamforge.common.model.CanonicalEvent;
import io.streamforge.common.model.CanonicalSchemaVersion;
import io.streamforge.common.model.EventId;
import io.streamforge.common.model.EventMetadata;
import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentReference;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.Quantity;
import io.streamforge.common.model.RawEventReference;
import io.streamforge.common.model.SequenceNumber;
import io.streamforge.common.model.Side;
import io.streamforge.common.model.Trade;
import io.streamforge.common.model.TradeId;
import io.streamforge.common.model.Venue;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Streaming CSV adapter that maps explicit source columns into canonical trade events. */
public final class CsvInputAdapter {

  /**
   * Reads one configured CSV record at a time and emits canonical events or typed row errors.
   *
   * <p>The supplied reader is owned by the caller and is not closed by this method. A successful
   * row uses its one-based data-row ordinal as both the canonical sequence number and trade ID; the
   * physical CSV row remains available on the emitted result.
   */
  public CsvProcessingResult process(
      Reader input, CsvAdapterConfig config, CsvMode mode, Consumer<CsvEvent> eventConsumer)
      throws IOException {
    if (input == null || config == null || mode == null || eventConsumer == null) {
      throw new IllegalArgumentException(
          "input, config, mode, and event consumer must not be null");
    }

    CsvRecordReader reader = new CsvRecordReader(input, config.delimiter());
    Optional<CsvRecordReader.CsvRecord> first;
    try {
      first = reader.next();
    } catch (CsvRecordReader.CsvSyntaxException error) {
      eventConsumer.accept(
          error(error.row(), CsvErrorReason.MALFORMED_ROW, error.getMessage(), ""));
      return new CsvProcessingResult(0, 0, 1);
    }

    if (first.isEmpty()) {
      return new CsvProcessingResult(0, 0, 0);
    }

    ColumnResolver resolver;
    long headerRow = first.orElseThrow().startLine();
    try {
      resolver = ColumnResolver.create(first.orElseThrow().fields(), config);
    } catch (CsvInputException error) {
      eventConsumer.accept(
          error(headerRow, error.reason, error.getMessage(), first.orElseThrow().sourceText()));
      return new CsvProcessingResult(0, 0, 1);
    }

    CsvProcessingResultBuilder counts = new CsvProcessingResultBuilder();
    Optional<CsvRecordReader.CsvRecord> current;
    try {
      current = config.hasHeader() ? reader.next() : first;
    } catch (CsvRecordReader.CsvSyntaxException error) {
      eventConsumer.accept(
          error(error.row(), CsvErrorReason.MALFORMED_ROW, error.getMessage(), ""));
      return new CsvProcessingResult(0, 0, 1);
    }
    long dataRowOrdinal = 0;
    while (current.isPresent()) {
      CsvRecordReader.CsvRecord record = current.orElseThrow();
      dataRowOrdinal++;
      counts.rowsRead++;
      CsvEvent result;
      try {
        result = normalize(record, dataRowOrdinal, resolver, config);
      } catch (CsvInputException error) {
        result = error(record.startLine(), error.reason, error.getMessage(), record.sourceText());
      } catch (RuntimeException error) {
        result =
            error(
                record.startLine(),
                CsvErrorReason.NORMALIZATION_ERROR,
                detail(error),
                record.sourceText());
      }
      eventConsumer.accept(result);
      if (result instanceof CsvCanonicalEvent) {
        counts.eventsProduced++;
      } else {
        counts.errorsReported++;
        if (mode == CsvMode.FAIL_FAST) {
          break;
        }
      }
      try {
        current = reader.next();
      } catch (CsvRecordReader.CsvSyntaxException error) {
        eventConsumer.accept(
            error(error.row(), CsvErrorReason.MALFORMED_ROW, error.getMessage(), ""));
        counts.errorsReported++;
        break;
      }
    }
    return counts.result();
  }

  private CsvCanonicalEvent normalize(
      CsvRecordReader.CsvRecord record,
      long sequence,
      ColumnResolver resolver,
      CsvAdapterConfig config) {
    String timestampText = resolver.value(record.fields(), config.timestampColumn(), "timestamp");
    EventTimestamp timestamp = parseTimestamp(timestampText, config.timestampFormat());
    InstrumentSymbol symbol =
        parseSymbol(resolver.value(record.fields(), config.symbolColumn(), "symbol"));
    Venue venue =
        config
            .constantVenue()
            .orElseGet(
                () ->
                    parseVenue(
                        resolver.value(
                            record.fields(), config.venueColumn().orElseThrow(), "venue")));
    FixedDecimal price =
        config
            .priceMantissaColumn()
            .map(
                column ->
                    parseMantissa(
                        resolver.value(record.fields(), column, "price mantissa"),
                        config.priceScale()))
            .orElseGet(
                () ->
                    parseDecimalPrice(
                        resolver.value(
                            record.fields(),
                            config.decimalPriceColumn().orElseThrow(),
                            "decimal price"),
                        config.priceScale()));
    Quantity quantity =
        parseQuantity(resolver.value(record.fields(), config.quantityColumn(), "quantity"));
    Side side =
        config.sideMapping().get(resolver.value(record.fields(), config.sideColumn(), "side"));
    if (side == null) {
      throw new CsvInputException(
          CsvErrorReason.INVALID_SIDE, "side value is not present in configured enum mapping");
    }

    SequenceNumber sequenceNumber = new SequenceNumber(sequence);
    EventId eventId = EventId.deterministic(config.source(), sequenceNumber);
    EventMetadata metadata =
        new EventMetadata(
            eventId,
            CanonicalSchemaVersion.V1_0,
            config.source(),
            venue,
            timestamp,
            Optional.empty(),
            sequenceNumber,
            new RawEventReference("csv:" + config.source().value() + ":row:" + record.startLine()));
    CanonicalEvent event =
        new CanonicalEvent(
            metadata,
            new InstrumentReference(symbol),
            new Trade(new TradeId(sequence), Optional.of(side), quantity, price));
    return new CsvCanonicalEvent(record.startLine(), event, record.sourceText());
  }

  private static EventTimestamp parseTimestamp(String value, CsvTimestampFormat format) {
    try {
      return switch (format) {
        case EPOCH_NANOS -> new EventTimestamp(Long.parseLong(value));
        case ISO_INSTANT -> fromInstant(Instant.parse(value));
      };
    } catch (DateTimeException | ArithmeticException | IllegalArgumentException error) {
      throw new CsvInputException(
          CsvErrorReason.INVALID_TIMESTAMP, "invalid " + format + " timestamp");
    }
  }

  private static EventTimestamp fromInstant(Instant instant) {
    long secondsNanos = Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L);
    return new EventTimestamp(Math.addExact(secondsNanos, instant.getNano()));
  }

  private static InstrumentSymbol parseSymbol(String value) {
    try {
      return new InstrumentSymbol(value);
    } catch (IllegalArgumentException error) {
      throw new CsvInputException(CsvErrorReason.INVALID_SYMBOL, "invalid instrument symbol");
    }
  }

  private static Venue parseVenue(String value) {
    try {
      return new Venue(value);
    } catch (IllegalArgumentException error) {
      throw new CsvInputException(CsvErrorReason.INVALID_VENUE, "invalid venue");
    }
  }

  private static FixedDecimal parseMantissa(String value, int scale) {
    try {
      return new FixedDecimal(Long.parseLong(value), scale);
    } catch (IllegalArgumentException error) {
      throw new CsvInputException(CsvErrorReason.INVALID_DECIMAL, "invalid price mantissa");
    }
  }

  private static FixedDecimal parseDecimalPrice(String value, int scale) {
    try {
      BigDecimal scaled = new BigDecimal(value).scaleByPowerOfTen(scale);
      BigInteger mantissa = scaled.toBigIntegerExact();
      if (mantissa.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0
          || mantissa.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
        throw new ArithmeticException("price mantissa is outside the Java long range");
      }
      return new FixedDecimal(mantissa.longValueExact(), scale);
    } catch (ArithmeticException | IllegalArgumentException error) {
      throw new CsvInputException(
          CsvErrorReason.INVALID_DECIMAL, "decimal price is not exact at configured scale");
    }
  }

  private static Quantity parseQuantity(String value) {
    try {
      return new Quantity(Long.parseLong(value));
    } catch (IllegalArgumentException error) {
      throw new CsvInputException(CsvErrorReason.INVALID_QUANTITY, "invalid quantity");
    }
  }

  private static CsvError error(long row, CsvErrorReason reason, String detail, String sourceText) {
    return new CsvError(
        row, reason, detail == null || detail.isBlank() ? reason.name() : detail, sourceText);
  }

  private static String detail(RuntimeException error) {
    return error.getMessage() == null || error.getMessage().isBlank()
        ? error.getClass().getSimpleName()
        : error.getMessage();
  }

  private static final class CsvProcessingResultBuilder {
    private long rowsRead;
    private long eventsProduced;
    private long errorsReported;

    private CsvProcessingResult result() {
      return new CsvProcessingResult(rowsRead, eventsProduced, errorsReported);
    }
  }

  private static final class ColumnResolver {
    private final Map<String, Integer> indexes;

    private ColumnResolver(Map<String, Integer> indexes) {
      this.indexes = indexes;
    }

    private static ColumnResolver create(List<String> headers, CsvAdapterConfig config) {
      if (!config.hasHeader()) {
        return new ColumnResolver(Map.of());
      }
      Map<String, Integer> indexes = new HashMap<>();
      for (int index = 0; index < headers.size(); index++) {
        if (indexes.put(headers.get(index), index) != null) {
          throw new CsvInputException(
              CsvErrorReason.INVALID_CONFIGURATION, "duplicate CSV header: " + headers.get(index));
        }
      }
      for (String column : configuredColumns(config)) {
        if (!indexes.containsKey(column)) {
          throw new CsvInputException(
              CsvErrorReason.MISSING_COLUMN, "CSV header is missing column: " + column);
        }
      }
      return new ColumnResolver(Map.copyOf(indexes));
    }

    private String value(List<String> fields, String column, String description) {
      int index = index(column, description);
      if (index < 0 || index >= fields.size()) {
        throw new CsvInputException(
            CsvErrorReason.MISSING_COLUMN, description + " column is not present in row");
      }
      return fields.get(index);
    }

    private int index(String column, String description) {
      if (!indexes.isEmpty()) {
        Integer index = indexes.get(column);
        if (index == null) {
          throw new CsvInputException(
              CsvErrorReason.MISSING_COLUMN, "CSV header is missing column: " + column);
        }
        return index;
      }
      try {
        int index = Integer.parseInt(column);
        if (index < 0) {
          throw new NumberFormatException();
        }
        return index;
      } catch (NumberFormatException error) {
        throw new CsvInputException(
            CsvErrorReason.INVALID_CONFIGURATION,
            description
                + " must be a nonnegative zero-based column index when no header is present");
      }
    }

    private static List<String> configuredColumns(CsvAdapterConfig config) {
      List<String> columns = new ArrayList<>();
      columns.add(config.timestampColumn());
      columns.add(config.symbolColumn());
      config.venueColumn().ifPresent(columns::add);
      config.priceMantissaColumn().ifPresent(columns::add);
      config.decimalPriceColumn().ifPresent(columns::add);
      columns.add(config.quantityColumn());
      columns.add(config.sideColumn());
      return columns;
    }
  }

  private static final class CsvInputException extends RuntimeException {
    private final CsvErrorReason reason;

    private CsvInputException(CsvErrorReason reason, String message) {
      super(message);
      this.reason = reason;
    }
  }
}
