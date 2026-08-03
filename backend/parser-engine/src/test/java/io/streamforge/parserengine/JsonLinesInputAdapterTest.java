package io.streamforge.parserengine;

import static org.assertj.core.api.Assertions.assertThat;

import io.streamforge.common.model.CanonicalEvent;
import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.OrderExecuted;
import io.streamforge.common.model.Trade;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonLinesInputAdapterTest {

  private final JsonLinesInputAdapter adapter = new JsonLinesInputAdapter();

  @Test
  void streamsValidJsonLinesIntoCanonicalEvents() throws IOException {
    List<JsonLinesEvent> results = new ArrayList<>();

    JsonLinesProcessingResult summary =
        adapter.process(new StringReader(VALID_LINES), JsonLinesMode.FAIL_FAST, results::add);

    assertThat(summary).isEqualTo(new JsonLinesProcessingResult(4, 4, 0));
    assertThat(results).hasSize(4).allMatch(JsonLinesCanonicalEvent.class::isInstance);
    CanonicalEvent first = ((JsonLinesCanonicalEvent) results.get(0)).event();
    assertThat(first.metadata().exchangeTimestamp()).isEqualTo(new EventTimestamp(Long.MAX_VALUE));
    assertThat(first.metadata().receiveTimestamp()).contains(new EventTimestamp(Long.MAX_VALUE));
    assertThat(first.metadata().sequenceNumber().value()).isEqualTo(1);
    assertThat(first.metadata().eventId().value())
        .isEqualTo("c0676afdc91e20ff9e2d002343271ce62b63dbf2c9d59d27d23f59fd71a67072");

    CanonicalEvent second = ((JsonLinesCanonicalEvent) results.get(1)).event();
    assertThat(second.metadata().exchangeTimestamp()).isEqualTo(new EventTimestamp(0));
    assertThat(second.metadata().receiveTimestamp()).isEmpty();
    assertThat(second.payload())
        .isEqualTo(
            new OrderExecuted(
                new io.streamforge.common.model.OrderId(1001),
                new io.streamforge.common.model.Quantity(40)));

    CanonicalEvent fourth = ((JsonLinesCanonicalEvent) results.get(3)).event();
    Trade trade = (Trade) fourth.payload();
    assertThat(trade.price().mantissa()).isEqualTo(25_005);
    assertThat(trade.price().scale()).isEqualTo(2);
  }

  @Test
  void continuesAfterMalformedAndInvalidLinesWithSourceLocations() throws IOException {
    String input =
        VALID_LINES.lines().findFirst().orElseThrow()
            + "\nnot-json\n"
            + VALID_LINES
                .lines()
                .skip(1)
                .findFirst()
                .orElseThrow()
                .replace("\"minor\":0", "\"minor\":1")
            + "\n"
            + VALID_LINES
                .lines()
                .skip(2)
                .findFirst()
                .orElseThrow()
                .replace(
                    "aa582985d538957ed8087862b2b549a3ba7cfd515e7ed0539c9b9dd64d6569b4",
                    "0000000000000000000000000000000000000000000000000000000000000000")
            + "\n"
            + VALID_LINES.lines().skip(3).findFirst().orElseThrow();
    List<JsonLinesEvent> results = new ArrayList<>();

    JsonLinesProcessingResult summary =
        adapter.process(new StringReader(input), JsonLinesMode.CONTINUE_WITH_ERRORS, results::add);

    assertThat(summary).isEqualTo(new JsonLinesProcessingResult(5, 2, 3));
    assertThat(results).hasSize(5);
    assertThat(((JsonLinesError) results.get(1)).lineNumber()).isEqualTo(2);
    assertThat(((JsonLinesError) results.get(1)).reason())
        .isEqualTo(JsonLinesErrorReason.MALFORMED_JSON);
    assertThat(((JsonLinesError) results.get(2)).lineNumber()).isEqualTo(3);
    assertThat(((JsonLinesError) results.get(2)).reason())
        .isEqualTo(JsonLinesErrorReason.SCHEMA_VERSION);
    assertThat(((JsonLinesError) results.get(3)).lineNumber()).isEqualTo(4);
    assertThat(((JsonLinesError) results.get(3)).reason())
        .isEqualTo(JsonLinesErrorReason.EVENT_ID_MISMATCH);
    assertThat(results.get(4)).isInstanceOf(JsonLinesCanonicalEvent.class);
  }

  @Test
  void stopsAtTheFirstErrorInFailFastMode() throws IOException {
    String input =
        VALID_LINES.lines().findFirst().orElseThrow()
            + "\nnot-json\n"
            + VALID_LINES.lines().skip(1).findFirst().orElseThrow();
    List<JsonLinesEvent> results = new ArrayList<>();

    JsonLinesProcessingResult summary =
        adapter.process(new StringReader(input), JsonLinesMode.FAIL_FAST, results::add);

    assertThat(summary).isEqualTo(new JsonLinesProcessingResult(2, 1, 1));
    assertThat(results).hasSize(2);
    assertThat(results.get(0)).isInstanceOf(JsonLinesCanonicalEvent.class);
    assertThat(((JsonLinesError) results.get(1)).lineNumber()).isEqualTo(2);
    assertThat(((JsonLinesError) results.get(1)).reason())
        .isEqualTo(JsonLinesErrorReason.MALFORMED_JSON);
  }

  @Test
  void doesNotCloseTheCallerOwnedReader() throws IOException {
    TrackingReader reader = new TrackingReader(VALID_LINES);

    adapter.process(reader, JsonLinesMode.FAIL_FAST, ignored -> {});

    assertThat(reader.closed).isFalse();
  }

  @Test
  void reportsNullInputAsAnInvalidArgument() {
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> adapter.process(null, JsonLinesMode.FAIL_FAST, ignored -> {}));
  }

  private static final String VALID_LINES =
      """
      {"metadata":{"eventId":"c0676afdc91e20ff9e2d002343271ce62b63dbf2c9d59d27d23f59fd71a67072","schemaVersion":{"major":1,"minor":0},"source":"jsonl/fixture-1","venue":"XNAS","exchangeTimestamp":9223372036854775807,"receiveTimestamp":9223372036854775807,"sequenceNumber":1,"rawEventReference":"jsonl-fixture:line-1"},"instrument":{"symbol":"AAPL"},"payload":{"type":"ORDER_ADDED","orderId":1001,"side":"BUY","quantity":100,"price":{"mantissa":12345,"scale":2}}}
      {"metadata":{"eventId":"8f1bcefa4c3a6f08f8269ff931996b31678e5a86a09a9e512e19101849089f44","schemaVersion":{"major":1,"minor":0},"source":"jsonl/fixture-1","venue":"XNAS","exchangeTimestamp":0,"sequenceNumber":2,"rawEventReference":"jsonl-fixture:line-2"},"instrument":{"symbol":"AAPL"},"payload":{"type":"ORDER_EXECUTED","orderId":1001,"executedQuantity":40}}
      {"metadata":{"eventId":"aa582985d538957ed8087862b2b549a3ba7cfd515e7ed0539c9b9dd64d6569b4","schemaVersion":{"major":1,"minor":0},"source":"jsonl/fixture-1","venue":"XNAS","exchangeTimestamp":1000000200,"sequenceNumber":3,"rawEventReference":"jsonl-fixture:line-3"},"instrument":{"symbol":"AAPL"},"payload":{"type":"ORDER_CANCELLED","orderId":1001,"cancelledQuantity":60}}
      {"metadata":{"eventId":"938964e8a04762eedb4174e08da3365161c3ed051a9a121bc0755c8a280e07b5","schemaVersion":{"major":1,"minor":0},"source":"jsonl/fixture-1","venue":"XNAS","exchangeTimestamp":1000000300,"receiveTimestamp":1000000399,"sequenceNumber":4,"rawEventReference":"jsonl-fixture:line-4"},"instrument":{"symbol":"MSFT"},"payload":{"type":"TRADE","tradeId":5001,"aggressorSide":"SELL","quantity":25,"price":{"mantissa":25005,"scale":2}}}
      """;

  private static final class TrackingReader extends StringReader {
    private boolean closed;

    private TrackingReader(String value) {
      super(value);
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
