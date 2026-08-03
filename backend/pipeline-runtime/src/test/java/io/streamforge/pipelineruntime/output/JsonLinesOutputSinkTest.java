package io.streamforge.pipelineruntime.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.streamforge.common.model.CanonicalEvent;
import io.streamforge.common.model.CanonicalSchemaVersion;
import io.streamforge.common.model.EventMetadata;
import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentReference;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.OrderAdded;
import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.Quantity;
import io.streamforge.common.model.RawEventReference;
import io.streamforge.common.model.SequenceNumber;
import io.streamforge.common.model.Side;
import io.streamforge.common.model.SourceIdentity;
import io.streamforge.common.model.Venue;
import io.streamforge.transform.compile.CanonicalTransformationFields;
import io.streamforge.transform.compile.CompiledTransformation;
import io.streamforge.transform.compile.TransformationCompiler;
import io.streamforge.transform.config.TransformationConfigParser;
import io.streamforge.transform.execute.TransformationExecutor;
import io.streamforge.transform.execute.TransformationResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonLinesOutputSinkTest {
  @TempDir Path temporaryDirectory;

  @Test
  void writesEscapedNestedRecordsWithExactNumbersAndTimestamps() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    JsonLinesOutputSink sink = new JsonLinesOutputSink(output);

    sink.start();
    sink.write(
        new OutputRecord(
            Map.of(
                "timestamp",
                9_007_199_254_740_993L,
                "price",
                new FixedDecimal(1_234_500, 4),
                "active",
                true,
                "details",
                Map.of("text", "AAPL, \"quoted\"\nvalue"),
                "items",
                List.of("one", 2L))));
    sink.complete();

    assertThat(output.toString(StandardCharsets.UTF_8))
        .isEqualTo(
            "{\"active\":true,\"details\":{\"text\":\"AAPL, \\\"quoted\\\"\\nvalue\"},\"items\":[\"one\",2],\"price\":123.4500,\"timestamp\":9007199254740993}\n");
  }

  @Test
  void writesACompiledTransformationDocumentIncrementally() throws Exception {
    CompiledTransformation transformation =
        new TransformationCompiler()
            .compile(
                new TransformationConfigParser()
                    .parse(
                        """
                        {"schemaVersion":"1.0","operations":[{"op":"rename","from":"instrument.symbol","to":"instrument.ticker"}]}
                        """),
                CanonicalTransformationFields.v1());
    TransformationResult result = new TransformationExecutor(transformation).execute(aaplEvent());
    OutputRecord record = OutputRecord.from(((TransformationResult.Transformed) result).document());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    JsonLinesOutputSink sink = new JsonLinesOutputSink(output);

    sink.start();
    sink.write(record);
    sink.complete();

    assertThat(output.toString(StandardCharsets.UTF_8)).contains("\"ticker\":\"AAPL\"");
  }

  @Test
  void retainsCallerStreamOwnershipAndAllowsEmptyStreams() throws Exception {
    TrackingOutputStream output = new TrackingOutputStream();
    JsonLinesOutputSink sink = new JsonLinesOutputSink(output);

    sink.start();
    sink.complete();

    assertThat(output.bytes()).isEmpty();
    assertThat(output.closed).isFalse();
  }

  @Test
  void flushesOnlyWhenExplicitlyRequestedAndClosesTransferredStreams() throws Exception {
    TrackingOutputStream output = new TrackingOutputStream();
    JsonLinesOutputSink sink = JsonLinesOutputSink.owning(output);

    sink.start();
    sink.write(new OutputRecord(Map.of("symbol", "AAPL")));
    assertThat(output.bytes()).isEmpty();
    sink.flush();
    assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("{\"symbol\":\"AAPL\"}\n");
    sink.complete();

    assertThat(output.closed).isTrue();
  }

  @Test
  void publishesFiniteFilesOnlyAfterSuccessfulCompletion() throws Exception {
    Path destination = temporaryDirectory.resolve("events.jsonl");
    Files.writeString(destination, "previous\n");
    JsonLinesOutputSink sink = new JsonLinesOutputSink(destination);

    sink.start();
    sink.write(new OutputRecord(Map.of("symbol", "AAPL")));
    assertThat(Files.readString(destination)).isEqualTo("previous\n");
    sink.complete();

    assertThat(Files.readString(destination)).isEqualTo("{\"symbol\":\"AAPL\"}\n");
    try (var files = Files.list(temporaryDirectory)) {
      assertThat(files.map(Path::getFileName).map(Path::toString))
          .noneMatch(name -> name.startsWith(".streamforge-output-"));
    }
  }

  @Test
  void reportsWriteFailuresAsTypedPipelineFailures() throws Exception {
    JsonLinesOutputSink sink = new JsonLinesOutputSink(new FailingOutputStream());
    sink.start();

    assertThatThrownBy(() -> sink.write(new OutputRecord(Map.of("text", "x".repeat(9_000)))))
        .isInstanceOfSatisfying(
            OutputSinkException.class,
            exception ->
                assertThat(exception.failure().stage()).isEqualTo(OutputSinkFailureStage.WRITE));
    assertThatThrownBy(sink::complete)
        .isInstanceOfSatisfying(
            OutputSinkException.class,
            exception ->
                assertThat(exception.failure().stage()).isEqualTo(OutputSinkFailureStage.COMPLETE));
  }

  private static final class TrackingOutputStream extends ByteArrayOutputStream {
    private boolean closed;

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }

    private byte[] bytes() {
      return toByteArray();
    }
  }

  private static final class FailingOutputStream extends OutputStream {
    @Override
    public void write(int value) throws IOException {
      throw new IOException("simulated write failure");
    }

    @Override
    public void write(byte[] values, int offset, int length) throws IOException {
      throw new IOException("simulated write failure");
    }
  }

  private static CanonicalEvent aaplEvent() {
    return new CanonicalEvent(
        EventMetadata.create(
            CanonicalSchemaVersion.V1_0,
            new SourceIdentity("sink-test"),
            new Venue("XNAS"),
            new EventTimestamp(1_000_000_000L),
            Optional.empty(),
            new SequenceNumber(1),
            new RawEventReference("sink:test")),
        new InstrumentReference(new InstrumentSymbol("AAPL")),
        new OrderAdded(
            new OrderId(1), Side.BUY, new Quantity(100), new FixedDecimal(1_234_500, 4)));
  }
}
