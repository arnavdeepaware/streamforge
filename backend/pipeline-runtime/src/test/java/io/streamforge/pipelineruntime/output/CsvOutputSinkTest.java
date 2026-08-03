package io.streamforge.pipelineruntime.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.streamforge.common.model.FixedDecimal;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CsvOutputSinkTest {
  private static final CsvOutputConfig CONFIG =
      new CsvOutputConfig(
          List.of(
              CsvOutputColumn.of("symbol", "instrument.symbol"),
              CsvOutputColumn.of("timestamp", "metadata.exchangeTimestamp"),
              CsvOutputColumn.of("price", "payload.price"),
              CsvOutputColumn.of("note", "note"),
              CsvOutputColumn.of("missing", "missing")),
          true);

  @Test
  void writesConfiguredColumnOrderEscapingAndExactValues() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    CsvOutputSink sink = new CsvOutputSink(output, CONFIG);

    sink.start();
    sink.write(
        new OutputRecord(
            new LinkedHashMap<>(
                Map.of(
                    "note", "quote \"and\", newline\n",
                    "payload", Map.of("price", new FixedDecimal(1_234_500, 4)),
                    "instrument", Map.of("symbol", "AAPL"),
                    "metadata", Map.of("exchangeTimestamp", 9_007_199_254_740_993L)))));
    sink.complete();

    assertThat(output.toString())
        .isEqualTo(
            "symbol,timestamp,price,note,missing\n"
                + "AAPL,9007199254740993,123.4500,\"quote \"\"and\"\", newline\n\",\n");
  }

  @Test
  void writesHeaderForAnEmptyStream() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    CsvOutputSink sink = new CsvOutputSink(output, CONFIG);

    sink.start();
    sink.complete();

    assertThat(output.toString()).isEqualTo("symbol,timestamp,price,note,missing\n");
  }

  @Test
  void rejectsNonScalarConfiguredColumnsAsTypedWriteFailures() throws Exception {
    CsvOutputSink sink =
        new CsvOutputSink(
            new ByteArrayOutputStream(),
            new CsvOutputConfig(List.of(CsvOutputColumn.of("payload", "payload")), false));
    sink.start();

    assertThatThrownBy(() -> sink.write(new OutputRecord(Map.of("payload", Map.of("price", 1L)))))
        .isInstanceOfSatisfying(
            OutputSinkException.class,
            exception ->
                assertThat(exception.failure().stage()).isEqualTo(OutputSinkFailureStage.WRITE));
  }
}
