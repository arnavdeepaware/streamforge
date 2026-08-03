package io.streamforge.pipelineruntime;

import static org.assertj.core.api.Assertions.assertThat;

import io.streamforge.common.model.Side;
import io.streamforge.common.model.SourceIdentity;
import io.streamforge.common.model.Venue;
import io.streamforge.parserengine.JsonLinesMode;
import io.streamforge.parserengine.csv.CsvAdapterConfig;
import io.streamforge.parserengine.csv.CsvMode;
import io.streamforge.parserengine.csv.CsvTimestampFormat;
import io.streamforge.pipelineruntime.deadletter.DeadLetterConfig;
import io.streamforge.stp.protocol.StpProtocol;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDeadLetterHandlingTest {
  @TempDir Path temporaryDirectory;

  @Test
  void quarantinesMalformedJsonlWithBoundedPayloadAndContinues() throws Exception {
    Path input = temporaryDirectory.resolve("events.jsonl");
    Files.writeString(input, "{\"password\":\"do-not-retain\"}\n" + validJson());
    Path deadLetters = temporaryDirectory.resolve("dead.jsonl");

    PipelineReport report = run(jsonInput(input), deadLetters, Optional.empty(), 64);

    assertThat(report.counters()).isEqualTo(new PipelineCounters(2, 1, 1, 0, 1, 1));
    assertThat(report.outcome()).isEqualTo(PipelineOutcome.COMPLETED);
    String record = Files.readString(deadLetters);
    assertThat(record)
        .contains("\"category\":\"MALFORMED_INPUT\"")
        .contains("\"stage\":\"PARSE\"")
        .contains("\"timestamp\":\"2026-08-03T00:00:00Z\"")
        .doesNotContain("do-not-retain");
  }

  @Test
  void quarantinesCsvAndStpParseFailuresWithoutStoppingIndependentRecords() throws Exception {
    Path csv = temporaryDirectory.resolve("trades.csv");
    Files.writeString(
        csv,
        "timestamp,symbol,venue,price,quantity,side\n"
            + "1,AAPL,XNAS,12.345,1,B\n"
            + "2,AAPL,XNAS,12.34,1,B\n");
    Path csvDeadLetters = temporaryDirectory.resolve("csv-dead.jsonl");

    PipelineReport csvReport = run(csvInput(csv), csvDeadLetters, Optional.empty());

    assertThat(csvReport.counters()).isEqualTo(new PipelineCounters(2, 1, 1, 0, 1, 1));
    assertThat(Files.readString(csvDeadLetters))
        .contains("\"category\":\"MALFORMED_INPUT\"")
        .contains("\"truncated\":true")
        .contains("1,AAPL,XNAS,12.3");

    Path stp = temporaryDirectory.resolve("malformed.stp");
    Files.write(stp, new byte[] {0, 0});
    Path stpDeadLetters = temporaryDirectory.resolve("stp-dead.jsonl");

    PipelineReport stpReport = run(stpInput(stp), stpDeadLetters, Optional.empty());

    assertThat(stpReport.counters().failed()).isEqualTo(1);
    assertThat(Files.readString(stpDeadLetters))
        .contains("\"category\":\"MALFORMED_INPUT\"")
        .contains("\"encoding\":\"base64\"");
  }

  @Test
  void quarantinesTransformFailuresAndKeepsFailureIdsDeterministic() throws Exception {
    Path input = temporaryDirectory.resolve("events.jsonl");
    Files.writeString(input, validJson());
    Path transformation = temporaryDirectory.resolve("transform.json");
    Files.writeString(
        transformation,
        """
        {"schemaVersion":"1.0","operations":[
          {"op":"enum_map","path":"payload.aggressorSide","mapping":{"BUY":"B","SELL":"S"}}
        ]}
        """);
    Path first = temporaryDirectory.resolve("first.jsonl");
    Path second = temporaryDirectory.resolve("second.jsonl");

    PipelineReport firstReport = run(jsonInput(input), first, Optional.of(transformation));
    PipelineReport secondReport = run(jsonInput(input), second, Optional.of(transformation));

    assertThat(firstReport.counters()).isEqualTo(new PipelineCounters(1, 1, 1, 0, 0, 1));
    assertThat(Files.readString(first))
        .contains("\"category\":\"TRANSFORMATION\"")
        .contains("\"eventId\"");
    assertThat(Files.readString(first)).isEqualTo(Files.readString(second));
  }

  @Test
  void recordsOutputStartFailureAsRetryable() throws Exception {
    Path input = temporaryDirectory.resolve("events.jsonl");
    Files.writeString(input, validJson());
    Path blockingFile = temporaryDirectory.resolve("not-a-directory");
    Files.writeString(blockingFile, "block");
    Path deadLetters = temporaryDirectory.resolve("output-dead.jsonl");
    PipelineRunConfig config =
        new PipelineRunConfig(
            jsonInput(input),
            Optional.empty(),
            Optional.empty(),
            new PipelineOutput.JsonLines(blockingFile.resolve("result.jsonl")),
            new PipelineIdentity("dead-letter-test", "7"),
            Optional.of(DeadLetterConfig.quarantine(deadLetters, true, 64)));

    PipelineReport report = runner().run(config, new PipelineCancellation());

    assertThat(report.counters().failed()).isEqualTo(1);
    assertThat(report.outcome()).isEqualTo(PipelineOutcome.FAILED);
    assertThat(Files.readString(deadLetters))
        .contains("\"category\":\"OUTPUT\"")
        .contains("\"retryability\":\"RETRYABLE\"");
  }

  @Test
  void supportsSkipAndFailFastPolicies() throws Exception {
    Path input = temporaryDirectory.resolve("policies.jsonl");
    Files.writeString(input, "{}\n" + validJson());

    PipelineRunConfig skip =
        new PipelineRunConfig(
            jsonInput(input),
            Optional.empty(),
            Optional.empty(),
            new PipelineOutput.JsonLines(temporaryDirectory.resolve("skip.jsonl")),
            new PipelineIdentity("dead-letter-test", "7"),
            Optional.of(DeadLetterConfig.skip()));
    PipelineRunConfig failFast =
        new PipelineRunConfig(
            jsonInput(input),
            Optional.empty(),
            Optional.empty(),
            new PipelineOutput.JsonLines(temporaryDirectory.resolve("fail-fast.jsonl")),
            new PipelineIdentity("dead-letter-test", "7"),
            Optional.of(DeadLetterConfig.failFast()));

    PipelineReport skipped = runner().run(skip, new PipelineCancellation());
    PipelineReport stopped = runner().run(failFast, new PipelineCancellation());

    assertThat(skipped.counters()).isEqualTo(new PipelineCounters(2, 1, 1, 0, 1, 1));
    assertThat(skipped.outcome()).isEqualTo(PipelineOutcome.COMPLETED);
    assertThat(stopped.counters()).isEqualTo(new PipelineCounters(1, 0, 0, 0, 0, 1));
    assertThat(stopped.outcome()).isEqualTo(PipelineOutcome.FAILED);
  }

  private PipelineReport run(PipelineInput input, Path deadLetters, Optional<Path> transformation)
      throws Exception {
    return run(input, deadLetters, transformation, 16);
  }

  private PipelineReport run(
      PipelineInput input, Path deadLetters, Optional<Path> transformation, int maximumPayloadBytes)
      throws Exception {
    PipelineRunConfig config =
        new PipelineRunConfig(
            input,
            transformation,
            Optional.empty(),
            new PipelineOutput.JsonLines(
                temporaryDirectory.resolve(deadLetters.getFileName() + ".out")),
            new PipelineIdentity("dead-letter-test", "7"),
            Optional.of(DeadLetterConfig.quarantine(deadLetters, true, maximumPayloadBytes)));
    return runner().run(config, new PipelineCancellation());
  }

  private LocalPipelineRunner runner() {
    return new LocalPipelineRunner(
        20, Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC));
  }

  private static PipelineInput.JsonLines jsonInput(Path path) {
    return new PipelineInput.JsonLines(path, JsonLinesMode.CONTINUE_WITH_ERRORS);
  }

  private static PipelineInput.Csv csvInput(Path path) {
    return new PipelineInput.Csv(
        path,
        new CsvAdapterConfig(
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
            new SourceIdentity("dead-letter/csv")),
        CsvMode.CONTINUE_WITH_ERRORS);
  }

  private static PipelineInput.StpBinary stpInput(Path path) {
    return new PipelineInput.StpBinary(
        path,
        new SourceIdentity("dead-letter/stp"),
        new Venue("XNAS"),
        StpProtocol.LENGTH_FIELD_WIDTH + StpProtocol.MAX_ENCODED_LENGTH);
  }

  private static String validJson() {
    return """
        {"metadata":{"eventId":"c0676afdc91e20ff9e2d002343271ce62b63dbf2c9d59d27d23f59fd71a67072","schemaVersion":{"major":1,"minor":0},"source":"jsonl/fixture-1","venue":"XNAS","exchangeTimestamp":1000000000,"sequenceNumber":1,"rawEventReference":"pipeline-aapl:line-1"},"instrument":{"symbol":"AAPL"},"payload":{"type":"ORDER_ADDED","orderId":1001,"side":"BUY","quantity":100,"price":{"mantissa":12345,"scale":2}}}
        """;
  }
}
