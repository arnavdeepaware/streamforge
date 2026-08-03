package io.streamforge.parserengine.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.streamforge.common.model.CanonicalEvent;
import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.Side;
import io.streamforge.common.model.SourceIdentity;
import io.streamforge.common.model.Trade;
import io.streamforge.common.model.Venue;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CsvInputAdapterTest {

  private final CsvInputAdapter adapter = new CsvInputAdapter();

  @Test
  void handlesQuotedFieldsAndMapsSampleRowsToExactCanonicalTrades() throws IOException {
    String csv =
        "timestamp,symbol,venue,price,quantity,side,notes\n"
            + "9223372036854775807,\"AAPL\",\"XNAS\",\"123.45\",100,\"B\",\"quoted, comma\"\n"
            + "0,\"MSFT\",\"XNAS\",\"250.05\",25,\"S\",ordinary\n";
    List<CsvEvent> events = new ArrayList<>();

    CsvProcessingResult result =
        adapter.process(csvReader(csv), config(), CsvMode.FAIL_FAST, events::add);

    assertThat(result).isEqualTo(new CsvProcessingResult(2, 2, 0));
    CanonicalEvent first = ((CsvCanonicalEvent) events.get(0)).event();
    assertThat(first.metadata().exchangeTimestamp()).isEqualTo(new EventTimestamp(Long.MAX_VALUE));
    assertThat(first.metadata().sequenceNumber().value()).isEqualTo(1);
    assertThat(first.metadata().source()).isEqualTo(new SourceIdentity("csv/sample-trades"));
    assertThat(first.metadata().venue()).isEqualTo(new Venue("XNAS"));
    assertThat(first.instrument().symbol().value()).isEqualTo("AAPL");
    Trade firstTrade = (Trade) first.payload();
    assertThat(firstTrade.price().mantissa()).isEqualTo(12_345);
    assertThat(firstTrade.price().scale()).isEqualTo(2);
    assertThat(firstTrade.aggressorSide()).contains(Side.BUY);

    Trade secondTrade = (Trade) ((CsvCanonicalEvent) events.get(1)).event().payload();
    assertThat(secondTrade.price().mantissa()).isEqualTo(25_005);
    assertThat(secondTrade.aggressorSide()).contains(Side.SELL);
  }

  @Test
  void supportsConstantVenueMantissaPricesAndNumericColumnsWithoutAHeader() throws IOException {
    String csv = "9223372036854775807,AAPL,12345,100,B\n";
    CsvAdapterConfig config =
        new CsvAdapterConfig(
            ',',
            false,
            "0",
            CsvTimestampFormat.EPOCH_NANOS,
            "1",
            Optional.empty(),
            Optional.of(new Venue("XNAS")),
            Optional.of("2"),
            Optional.empty(),
            2,
            "3",
            "4",
            Map.of("B", Side.BUY),
            new SourceIdentity("csv/no-header"));
    List<CsvEvent> events = new ArrayList<>();

    adapter.process(csvReader(csv), config, CsvMode.FAIL_FAST, events::add);

    Trade trade = (Trade) ((CsvCanonicalEvent) events.get(0)).event().payload();
    assertThat(trade.price().mantissa()).isEqualTo(12_345);
    assertThat(((CsvCanonicalEvent) events.get(0)).event().metadata().venue())
        .isEqualTo(new Venue("XNAS"));
  }

  @Test
  void reportsMissingColumnsBeforeProcessingData() throws IOException {
    List<CsvEvent> events = new ArrayList<>();
    CsvAdapterConfig config = configWithTimestampColumn("missing_timestamp");

    CsvProcessingResult result =
        adapter.process(
            csvReader("timestamp,symbol,venue,price,quantity,side\n1,AAPL,XNAS,1.00,1,B\n"),
            config,
            CsvMode.CONTINUE_WITH_ERRORS,
            events::add);

    assertThat(result).isEqualTo(new CsvProcessingResult(0, 0, 1));
    assertThat(events)
        .containsExactly(
            new CsvError(
                1,
                CsvErrorReason.MISSING_COLUMN,
                "CSV header is missing column: missing_timestamp",
                "timestamp,symbol,venue,price,quantity,side\n"));
  }

  @Test
  void reportsInvalidDecimalsAndEnumValuesWithRowNumbersInContinueMode() throws IOException {
    String csv =
        "timestamp,symbol,venue,price,quantity,side\n"
            + "1,AAPL,XNAS,12.345,1,B\n"
            + "2,AAPL,XNAS,not-a-price,1,B\n"
            + "3,AAPL,XNAS,1.00,1,X\n"
            + "4,AAPL,XNAS,1.00,1,S\n";
    List<CsvEvent> events = new ArrayList<>();

    CsvProcessingResult result =
        adapter.process(csvReader(csv), config(), CsvMode.CONTINUE_WITH_ERRORS, events::add);

    assertThat(result).isEqualTo(new CsvProcessingResult(4, 1, 3));
    assertThat(((CsvError) events.get(0)).reason()).isEqualTo(CsvErrorReason.INVALID_DECIMAL);
    assertThat(((CsvError) events.get(0)).rowNumber()).isEqualTo(2);
    assertThat(((CsvError) events.get(1)).reason()).isEqualTo(CsvErrorReason.INVALID_DECIMAL);
    assertThat(((CsvError) events.get(1)).rowNumber()).isEqualTo(3);
    assertThat(((CsvError) events.get(2)).reason()).isEqualTo(CsvErrorReason.INVALID_SIDE);
    assertThat(((CsvError) events.get(2)).rowNumber()).isEqualTo(4);
    assertThat(events.get(3)).isInstanceOf(CsvCanonicalEvent.class);
  }

  @Test
  void failsFastOnMalformedQuotedRows() throws IOException {
    List<CsvEvent> events = new ArrayList<>();
    String csv =
        "timestamp,symbol,venue,price,quantity,side\n1,\"AAPL,XNAS,1.00,1,B\n2,AAPL,XNAS,1.00,1,B\n";

    CsvProcessingResult result =
        adapter.process(csvReader(csv), config(), CsvMode.FAIL_FAST, events::add);

    assertThat(result).isEqualTo(new CsvProcessingResult(0, 0, 1));
    assertThat(events.get(0))
        .isEqualTo(new CsvError(2, CsvErrorReason.MALFORMED_ROW, "quoted field is not closed"));
  }

  @Test
  void parsesNanosecondPreservingIsoInstants() throws IOException {
    CsvAdapterConfig config =
        new CsvAdapterConfig(
            ',',
            true,
            "time",
            CsvTimestampFormat.ISO_INSTANT,
            "symbol",
            Optional.empty(),
            Optional.of(new Venue("XNAS")),
            Optional.of("mantissa"),
            Optional.empty(),
            2,
            "quantity",
            "side",
            Map.of("B", Side.BUY),
            new SourceIdentity("csv/iso"));
    List<CsvEvent> events = new ArrayList<>();

    adapter.process(
        csvReader(
            "time,symbol,mantissa,quantity,side\n2026-08-03T00:00:00.123456789Z,AAPL,12345,1,B\n"),
        config,
        CsvMode.FAIL_FAST,
        events::add);

    assertThat(
            ((CsvCanonicalEvent) events.get(0))
                .event()
                .metadata()
                .exchangeTimestamp()
                .nanosecondsSinceEpoch())
        .isEqualTo(1_785_715_200_123_456_789L);
  }

  @Test
  void rejectsInvalidConfiguration() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new CsvAdapterConfig(
                    ',',
                    true,
                    "timestamp",
                    CsvTimestampFormat.EPOCH_NANOS,
                    "symbol",
                    Optional.of("venue"),
                    Optional.of(new Venue("XNAS")),
                    Optional.empty(),
                    Optional.of("price"),
                    2,
                    "quantity",
                    "side",
                    Map.of("B", Side.BUY),
                    new SourceIdentity("csv/config")));
  }

  private static CsvAdapterConfig config() {
    return new CsvAdapterConfig(
        ',',
        true,
        "timestamp",
        CsvTimestampFormat.EPOCH_NANOS,
        "symbol",
        Optional.of("venue"),
        Optional.empty(),
        Optional.empty(),
        Optional.of("price"),
        2,
        "quantity",
        "side",
        Map.of("B", Side.BUY, "S", Side.SELL),
        new SourceIdentity("csv/sample-trades"));
  }

  private static CsvAdapterConfig configWithTimestampColumn(String column) {
    CsvAdapterConfig base = config();
    return new CsvAdapterConfig(
        base.delimiter(),
        base.hasHeader(),
        column,
        base.timestampFormat(),
        base.symbolColumn(),
        base.venueColumn(),
        base.constantVenue(),
        base.priceMantissaColumn(),
        base.decimalPriceColumn(),
        base.priceScale(),
        base.quantityColumn(),
        base.sideColumn(),
        base.sideMapping(),
        base.source());
  }

  private static StringReader csvReader(String value) {
    return new StringReader(value);
  }
}
