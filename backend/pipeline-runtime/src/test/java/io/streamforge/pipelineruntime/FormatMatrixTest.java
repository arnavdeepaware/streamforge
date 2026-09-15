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
import io.streamforge.pipelineruntime.output.ParquetColumnType;
import io.streamforge.pipelineruntime.output.ParquetCompression;
import io.streamforge.pipelineruntime.output.ParquetOutputColumn;
import io.streamforge.pipelineruntime.output.ParquetOutputConfig;
import io.streamforge.stp.protocol.AddOrderMessage;
import io.streamforge.stp.protocol.FrameHeader;
import io.streamforge.stp.protocol.MessageType;
import io.streamforge.stp.protocol.StpEncoder;
import io.streamforge.stp.protocol.StpProtocol;
import io.streamforge.transform.config.FieldPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormatMatrixTest {
  @TempDir Path temporaryDirectory;

  @Test
  void runsEveryStpCsvJsonlToJsonlCsvParquetCombination() throws Exception {
    List<NamedInput> inputs = createInputs();

    for (NamedInput input : inputs) {
      for (String outputType : List.of("jsonl", "csv", "parquet")) {
        UUID runId = UUID.randomUUID();
        Path runDirectory = temporaryDirectory.resolve("artifacts").resolve(runId.toString());
        Path outputPath = temporaryDirectory.resolve(input.name() + "-to-" + outputType);
        PipelineOutput output = output(outputType, outputPath);
        PipelineRunConfig config =
            new PipelineRunConfig(input.input(), Optional.empty(), Optional.empty(), output);

        PipelineReport report =
            new LocalPipelineRunner()
                .run(
                    config,
                    new PipelineCancellation(),
                    new PipelineRunArtifacts(runId, runDirectory));

        assertThat(report.outcome())
            .as(input.name() + " to " + outputType)
            .isEqualTo(PipelineOutcome.COMPLETED);
        assertThat(report.counters().emitted()).isPositive();
        assertThat(Files.size(outputPath)).isPositive();
        assertThat(Files.size(runDirectory.resolve("raw-input.capture"))).isPositive();
        assertThat(Files.exists(runDirectory.resolve("raw-input-manifest.json"))).isTrue();
        if (outputType.equals("jsonl")) {
          assertThat(Files.readString(outputPath))
              .contains("capture:" + runId + ":" + input.locationKind() + ":");
        }
      }
    }
  }

  private List<NamedInput> createInputs() throws Exception {
    Path stp = temporaryDirectory.resolve("one.stp");
    Files.write(
        stp,
        new StpEncoder()
            .encode(
                new AddOrderMessage(
                    new FrameHeader(
                        StpProtocol.ADD_ORDER_ENCODED_LENGTH,
                        MessageType.ADD_ORDER,
                        new SequenceNumber(1),
                        new EventTimestamp(1_700_000_000_123_456_789L)),
                    new OrderId(7),
                    new InstrumentSymbol("AAPL"),
                    Side.BUY,
                    new Quantity(100),
                    new FixedDecimal(12_345, 2))));
    return List.of(
        new NamedInput(
            "stp",
            "frame",
            new PipelineInput.StpBinary(
                stp,
                new SourceIdentity("matrix/stp"),
                new Venue("XNAS"),
                StpProtocol.LENGTH_FIELD_WIDTH + StpProtocol.MAX_ENCODED_LENGTH)),
        new NamedInput(
            "csv",
            "row",
            new PipelineInput.Csv(
                example("csv-trades-v1.csv"), csvConfig(), CsvMode.CONTINUE_WITH_ERRORS)),
        new NamedInput(
            "jsonl",
            "line",
            new PipelineInput.JsonLines(
                example("pipeline-aapl-input.jsonl"), JsonLinesMode.CONTINUE_WITH_ERRORS)));
  }

  private static PipelineOutput output(String type, Path path) {
    return switch (type) {
      case "jsonl" -> new PipelineOutput.JsonLines(path);
      case "csv" ->
          new PipelineOutput.Csv(
              path,
              new CsvOutputConfig(
                  List.of(
                      CsvOutputColumn.of("symbol", "instrument.symbol"),
                      CsvOutputColumn.of("sequence", "metadata.sequenceNumber")),
                  true));
      case "parquet" ->
          new PipelineOutput.Parquet(
              path,
              new ParquetOutputConfig(
                  ParquetCompression.SNAPPY,
                  ParquetOutputConfig.MINIMUM_ROW_GROUP_SIZE_BYTES,
                  List.of(
                      parquetColumn("symbol", "instrument.symbol", ParquetColumnType.STRING),
                      parquetColumn(
                          "exchange_timestamp",
                          "metadata.exchangeTimestamp",
                          ParquetColumnType.TIMESTAMP_NANOS),
                      parquetColumn(
                          "sequence", "metadata.sequenceNumber", ParquetColumnType.INT64))));
      default -> throw new IllegalArgumentException("unsupported test output: " + type);
    };
  }

  private static ParquetOutputColumn parquetColumn(
      String name, String path, ParquetColumnType type) {
    return new ParquetOutputColumn(name, new FieldPath(path), type, true, OptionalInt.empty());
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
        new SourceIdentity("matrix/csv"));
  }

  private static Path example(String name) {
    Path direct = Path.of("schemas/examples", name);
    return Files.exists(direct) ? direct : Path.of("../../schemas/examples", name);
  }

  private record NamedInput(String name, String locationKind, PipelineInput input) {}
}
