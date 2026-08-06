package io.streamforge.pipelineruntime;

import static org.assertj.core.api.Assertions.assertThat;

import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.Quantity;
import io.streamforge.common.model.SequenceNumber;
import io.streamforge.common.model.Side;
import io.streamforge.common.model.SourceIdentity;
import io.streamforge.common.model.Venue;
import io.streamforge.parserengine.JsonLinesMode;
import io.streamforge.parserengine.csv.CsvAdapterConfig;
import io.streamforge.parserengine.csv.CsvMode;
import io.streamforge.parserengine.csv.CsvTimestampFormat;
import io.streamforge.pipelineruntime.output.CsvOutputColumn;
import io.streamforge.pipelineruntime.output.CsvOutputConfig;
import io.streamforge.stp.protocol.AddOrderMessage;
import io.streamforge.stp.protocol.CancelOrderMessage;
import io.streamforge.stp.protocol.ExecuteOrderMessage;
import io.streamforge.stp.protocol.FrameHeader;
import io.streamforge.stp.protocol.MessageType;
import io.streamforge.stp.protocol.StpEncoder;
import io.streamforge.stp.protocol.StpProtocol;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPipelineRunnerTest {
  @TempDir Path temporaryDirectory;

  @Test
  void convertsTheCheckedInJsonlBlueprintSampleToItsGoldenOutput() throws Exception {
    PipelineRunConfig loaded =
        new PipelineConfigLoader().load(example("pipeline-aapl-jsonl-v1.json"));
    Path output = temporaryDirectory.resolve("aapl.jsonl");
    PipelineRunConfig config =
        new PipelineRunConfig(
            loaded.input(),
            loaded.transformationConfig(),
            loaded.blueprintConfig(),
            new PipelineOutput.JsonLines(output));

    PipelineReport report = new LocalPipelineRunner().run(config, new PipelineCancellation());

    assertThat(report.counters()).isEqualTo(new PipelineCounters(1, 1, 1, 0, 1, 0));
    assertThat(report.outcome()).isEqualTo(PipelineOutcome.COMPLETED);
    assertThat(report.failures()).isEmpty();
    assertThat(Files.readString(output))
        .isEqualTo(Files.readString(example("pipeline-aapl-jsonl-golden-output.jsonl")));
  }

  @Test
  void streamsConfiguredCsvInputToConfiguredCsvColumns() throws Exception {
    Path output = temporaryDirectory.resolve("trades.csv");
    PipelineRunConfig config =
        new PipelineRunConfig(
            new PipelineInput.Csv(
                example("csv-trades-v1.csv"), csvConfig(), CsvMode.CONTINUE_WITH_ERRORS),
            Optional.empty(),
            Optional.empty(),
            new PipelineOutput.Csv(
                output,
                new CsvOutputConfig(
                    List.of(
                        CsvOutputColumn.of("symbol", "instrument.symbol"),
                        CsvOutputColumn.of("timestamp", "metadata.exchangeTimestamp"),
                        CsvOutputColumn.of("price", "payload.price"),
                        CsvOutputColumn.of("side", "payload.aggressorSide")),
                    true)));

    PipelineReport report = new LocalPipelineRunner().run(config, new PipelineCancellation());

    assertThat(report.counters()).isEqualTo(new PipelineCounters(2, 2, 2, 0, 2, 0));
    assertThat(Files.readString(output))
        .isEqualTo(
            "symbol,timestamp,price,side\n"
                + "AAPL,9223372036854775807,123.45,BUY\n"
                + "MSFT,0,250.05,SELL\n");
  }

  @Test
  void streamsStpFramesAndResolvesExecutionInstrumentsFromPriorAdds() throws Exception {
    Path input = temporaryDirectory.resolve("ticks.stp");
    StpEncoder encoder = new StpEncoder();
    byte[] add =
        encoder.encode(
            new AddOrderMessage(
                new FrameHeader(
                    StpProtocol.ADD_ORDER_ENCODED_LENGTH,
                    MessageType.ADD_ORDER,
                    new SequenceNumber(1),
                    new EventTimestamp(1_000_000_000L)),
                new OrderId(77),
                new InstrumentSymbol("AAPL"),
                Side.BUY,
                new Quantity(100),
                new FixedDecimal(12_345, 2)));
    byte[] execute =
        encoder.encode(
            new ExecuteOrderMessage(
                new FrameHeader(
                    StpProtocol.EXECUTE_ORDER_ENCODED_LENGTH,
                    MessageType.EXECUTE_ORDER,
                    new SequenceNumber(3),
                    new EventTimestamp(1_000_000_100L)),
                new OrderId(77),
                new Quantity(40)));
    Files.write(input, join(add, execute, execute));
    Path output = temporaryDirectory.resolve("ticks.jsonl");
    PipelineRunConfig config =
        new PipelineRunConfig(
            new PipelineInput.StpBinary(
                input,
                new SourceIdentity("stp/test"),
                new Venue("XNAS"),
                StpProtocol.LENGTH_FIELD_WIDTH + StpProtocol.MAX_ENCODED_LENGTH),
            Optional.empty(),
            Optional.empty(),
            new PipelineOutput.JsonLines(output));

    List<PipelineRunMetrics> liveMetrics = new ArrayList<>();
    PipelineReport report =
        new LocalPipelineRunner(
                100,
                Clock.systemUTC(),
                new PipelineRunObserver() {
                  @Override
                  public void onMetrics(PipelineRunMetrics metrics) {
                    liveMetrics.add(metrics);
                  }

                  @Override
                  public void onDeadLetter(
                      io.streamforge.pipelineruntime.deadletter.DeadLetterRecord record) {}
                })
            .run(config, new PipelineCancellation());

    assertThat(report.counters()).isEqualTo(new PipelineCounters(3, 3, 3, 0, 3, 0));
    assertThat(liveMetrics)
        .last()
        .satisfies(
            metrics -> {
              assertThat(metrics.counters()).isEqualTo(report.counters());
              assertThat(metrics.sequenceGapCount()).isEqualTo(1);
              assertThat(metrics.duplicateCount()).isEqualTo(1);
              assertThat(metrics.queueDepth()).isZero();
            });
    assertThat(Files.readString(output))
        .contains("\"type\":\"ORDER_ADDED\"")
        .contains("\"type\":\"ORDER_EXECUTED\"")
        .contains("\"symbol\":\"AAPL\"");
  }

  @Test
  void keepsStpInstrumentStateAfterPartialCancelUntilOrderIsFullyClosed() throws Exception {
    Path input = temporaryDirectory.resolve("partial-cancel.stp");
    StpEncoder encoder = new StpEncoder();
    OrderId orderId = new OrderId(88);
    Files.write(
        input,
        join(
            encoder.encode(
                new AddOrderMessage(
                    new FrameHeader(
                        StpProtocol.ADD_ORDER_ENCODED_LENGTH,
                        MessageType.ADD_ORDER,
                        new SequenceNumber(1),
                        new EventTimestamp(1_000_000_000L)),
                    orderId,
                    new InstrumentSymbol("AAPL"),
                    Side.BUY,
                    new Quantity(100),
                    new FixedDecimal(12_345, 2))),
            encoder.encode(
                new CancelOrderMessage(
                    new FrameHeader(
                        StpProtocol.CANCEL_ORDER_ENCODED_LENGTH,
                        MessageType.CANCEL_ORDER,
                        new SequenceNumber(2),
                        new EventTimestamp(1_000_000_100L)),
                    orderId,
                    new Quantity(40))),
            encoder.encode(
                new ExecuteOrderMessage(
                    new FrameHeader(
                        StpProtocol.EXECUTE_ORDER_ENCODED_LENGTH,
                        MessageType.EXECUTE_ORDER,
                        new SequenceNumber(3),
                        new EventTimestamp(1_000_000_200L)),
                    orderId,
                    new Quantity(60)))));
    Path output = temporaryDirectory.resolve("partial-cancel.jsonl");
    PipelineRunConfig config =
        new PipelineRunConfig(
            new PipelineInput.StpBinary(
                input,
                new SourceIdentity("stp/partial-cancel"),
                new Venue("XNAS"),
                StpProtocol.LENGTH_FIELD_WIDTH + StpProtocol.MAX_ENCODED_LENGTH),
            Optional.empty(),
            Optional.empty(),
            new PipelineOutput.JsonLines(output));

    PipelineReport report = new LocalPipelineRunner().run(config, new PipelineCancellation());

    assertThat(report.counters()).isEqualTo(new PipelineCounters(3, 3, 3, 0, 3, 0));
    assertThat(report.failures()).isEmpty();
    assertThat(Files.readString(output))
        .contains("\"type\":\"ORDER_CANCELLED\"")
        .contains("\"type\":\"ORDER_EXECUTED\"")
        .contains("\"symbol\":\"AAPL\"");
  }

  @Test
  void reportsSourceLocationsAndAbortsStagedOutputOnCancellation() throws Exception {
    Path input = temporaryDirectory.resolve("invalid.jsonl");
    Files.writeString(input, Files.readString(example("pipeline-aapl-input.jsonl")) + "{}\n");
    Path output = temporaryDirectory.resolve("cancelled.jsonl");
    PipelineRunConfig config =
        new PipelineRunConfig(
            new PipelineInput.JsonLines(input, JsonLinesMode.CONTINUE_WITH_ERRORS),
            Optional.empty(),
            Optional.empty(),
            new PipelineOutput.JsonLines(output));

    PipelineReport failures = new LocalPipelineRunner().run(config, new PipelineCancellation());
    assertThat(failures.outcome()).isEqualTo(PipelineOutcome.COMPLETED);
    assertThat(failures.counters().failed()).isEqualTo(1);
    assertThat(failures.failures().getFirst().stage()).isEqualTo(PipelineStage.PARSE);
    assertThat(failures.failures().getFirst().sourceLocation()).endsWith("line 2");

    PipelineCancellation cancellation = new PipelineCancellation();
    cancellation.cancel();
    Path cancelledOutput = temporaryDirectory.resolve("cancelled-only.jsonl");
    PipelineRunConfig cancelledConfig =
        new PipelineRunConfig(
            config.input(),
            config.transformationConfig(),
            config.blueprintConfig(),
            new PipelineOutput.JsonLines(cancelledOutput));
    PipelineReport cancelled = new LocalPipelineRunner().run(cancelledConfig, cancellation);
    assertThat(cancelled.outcome()).isEqualTo(PipelineOutcome.CANCELLED);
    assertThat(Files.exists(cancelledOutput)).isFalse();
  }

  @Test
  void reportsInputAndOutputFailuresAsTerminalOutcomesWithoutPublishingArtifacts()
      throws Exception {
    Path missingInput = temporaryDirectory.resolve("missing.jsonl");
    Path missingInputOutput = temporaryDirectory.resolve("missing-output.jsonl");
    PipelineReport inputFailure =
        new LocalPipelineRunner()
            .run(
                new PipelineRunConfig(
                    new PipelineInput.JsonLines(missingInput, JsonLinesMode.CONTINUE_WITH_ERRORS),
                    Optional.empty(),
                    Optional.empty(),
                    new PipelineOutput.JsonLines(missingInputOutput)),
                new PipelineCancellation());

    assertThat(inputFailure.outcome()).isEqualTo(PipelineOutcome.FAILED);
    assertThat(inputFailure.failures())
        .extracting(PipelineFailure::stage)
        .contains(PipelineStage.INPUT);
    assertThat(Files.exists(missingInputOutput)).isFalse();

    Path blockingParent = temporaryDirectory.resolve("not-a-directory");
    Files.writeString(blockingParent, "occupied");
    PipelineReport outputFailure =
        new LocalPipelineRunner()
            .run(
                new PipelineRunConfig(
                    new PipelineInput.JsonLines(
                        example("pipeline-aapl-input.jsonl"), JsonLinesMode.CONTINUE_WITH_ERRORS),
                    Optional.empty(),
                    Optional.empty(),
                    new PipelineOutput.JsonLines(blockingParent.resolve("output.jsonl"))),
                new PipelineCancellation());

    assertThat(outputFailure.outcome()).isEqualTo(PipelineOutcome.FAILED);
    assertThat(outputFailure.failures())
        .extracting(PipelineFailure::stage)
        .contains(PipelineStage.OUTPUT);
  }

  private static CsvAdapterConfig csvConfig() {
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

  private static byte[] join(byte[]... frames) {
    int totalLength = java.util.Arrays.stream(frames).mapToInt(frame -> frame.length).sum();
    byte[] combined = new byte[totalLength];
    int offset = 0;
    for (byte[] frame : frames) {
      System.arraycopy(frame, 0, combined, offset, frame.length);
      offset += frame.length;
    }
    return combined;
  }

  private static Path example(String name) {
    Path direct = Path.of("schemas/examples", name);
    return Files.exists(direct) ? direct : Path.of("../../schemas/examples", name);
  }
}
