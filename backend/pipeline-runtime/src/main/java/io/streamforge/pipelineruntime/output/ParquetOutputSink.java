package io.streamforge.pipelineruntime.output;

import io.streamforge.common.model.FixedDecimal;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;

/** Staged Parquet sink backed by a caller-supplied explicit schema. */
public final class ParquetOutputSink implements OutputSink {
  private final Path destination;
  private final ParquetOutputConfig config;
  private final MessageType schema;
  private final SimpleGroupFactory groupFactory;
  private State state = State.NEW;
  private Path temporaryFile;
  private ParquetWriter<Group> writer;

  public ParquetOutputSink(Path destination, ParquetOutputConfig config) {
    if (destination == null || config == null) {
      throw new IllegalArgumentException("Parquet destination and config must not be null");
    }
    this.destination = destination.toAbsolutePath();
    this.config = config;
    schema = MessageTypeParser.parseMessageType(schemaText(config));
    groupFactory = new SimpleGroupFactory(schema);
  }

  @Override
  public void start() throws OutputSinkException {
    requireState(State.NEW, OutputSinkFailureStage.START);
    try {
      Path parent = destination.getParent();
      if (parent == null) {
        throw new IOException("destination must have a parent directory");
      }
      Files.createDirectories(parent);
      temporaryFile = Files.createTempFile(parent, ".streamforge-output-", ".parquet.tmp");
      Files.delete(temporaryFile);
      writer =
          ExampleParquetWriter.builder(new LocalOutputFile(temporaryFile))
              .withType(schema)
              .withCompressionCodec(codec(config.compression()))
              .withRowGroupSize(config.rowGroupSizeBytes())
              .build();
      state = State.ACTIVE;
    } catch (IOException | RuntimeException exception) {
      fail();
      throw exception(OutputSinkFailureStage.START, exception);
    }
  }

  @Override
  public void write(OutputRecord record) throws OutputSinkException {
    if (record == null) {
      throw new IllegalArgumentException("output record must not be null");
    }
    requireState(State.ACTIVE, OutputSinkFailureStage.WRITE);
    try {
      Group group = groupFactory.newGroup();
      for (ParquetOutputColumn column : config.columns()) {
        Optional<Object> value = valueAt(record.fields(), column);
        if (value.isEmpty()) {
          if (column.required()) {
            throw new IOException("required Parquet column is missing: " + column.name());
          }
          continue;
        }
        append(group, column, value.get());
      }
      writer.write(group);
    } catch (IOException | RuntimeException exception) {
      fail();
      throw exception(OutputSinkFailureStage.WRITE, exception);
    }
  }

  @Override
  public void flush() throws OutputSinkException {
    requireState(State.ACTIVE, OutputSinkFailureStage.FLUSH);
    // Parquet row groups are flushed by the writer according to the configured size.
  }

  @Override
  public void complete() throws OutputSinkException {
    requireState(State.ACTIVE, OutputSinkFailureStage.COMPLETE);
    try {
      writer.close();
      writer = null;
      publish();
      state = State.COMPLETED;
    } catch (IOException | RuntimeException exception) {
      fail();
      throw exception(OutputSinkFailureStage.COMPLETE, exception);
    }
  }

  @Override
  public void abort() {
    if (state == State.NEW || state == State.ACTIVE) {
      fail();
    }
  }

  @Override
  public void close() {
    abort();
  }

  private void append(Group group, ParquetOutputColumn column, Object value) throws IOException {
    switch (column.type()) {
      case STRING -> {
        if (!(value instanceof String text)) {
          throw incompatible(column, value);
        }
        group.add(column.name(), text);
      }
      case BOOLEAN -> {
        if (!(value instanceof Boolean bool)) {
          throw incompatible(column, value);
        }
        group.add(column.name(), bool);
      }
      case INT64, TIMESTAMP_NANOS -> {
        if (!(value instanceof Long integer)) {
          throw incompatible(column, value);
        }
        group.add(column.name(), integer);
      }
      case FIXED_DECIMAL -> {
        if (!(value instanceof FixedDecimal decimal)) {
          throw incompatible(column, value);
        }
        long unscaled = rescale(decimal, column.scale().orElseThrow(), column.name());
        group.add(
            column.name(),
            Binary.fromConstantByteArray(BigInteger.valueOf(unscaled).toByteArray()));
      }
    }
  }

  private Optional<Object> valueAt(Map<String, Object> fields, ParquetOutputColumn column)
      throws IOException {
    Object current = fields;
    for (String segment : column.path().segments()) {
      if (!(current instanceof Map<?, ?> object)) {
        throw new IOException("Parquet column " + column.name() + " traverses a non-object path");
      }
      if (!object.containsKey(segment)) {
        return Optional.empty();
      }
      current = object.get(segment);
    }
    if (current instanceof Map<?, ?> || current instanceof java.util.List<?>) {
      throw new IOException("Parquet column " + column.name() + " references a non-scalar value");
    }
    return Optional.of(current);
  }

  private static long rescale(FixedDecimal decimal, int targetScale, String column)
      throws IOException {
    int difference = targetScale - decimal.scale();
    if (difference == 0) {
      return decimal.mantissa();
    }
    long factor = powerOfTen(Math.abs(difference));
    if (difference > 0) {
      try {
        return Math.multiplyExact(decimal.mantissa(), factor);
      } catch (ArithmeticException exception) {
        throw new IOException("Parquet decimal overflow for column " + column, exception);
      }
    }
    if (decimal.mantissa() % factor != 0) {
      throw new IOException("lossy Parquet decimal rescaling for column " + column);
    }
    return decimal.mantissa() / factor;
  }

  private static long powerOfTen(int exponent) {
    long value = 1;
    for (int index = 0; index < exponent; index++) {
      value = Math.multiplyExact(value, 10L);
    }
    return value;
  }

  private void publish() throws IOException {
    try {
      Files.move(
          temporaryFile,
          destination,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
    }
    temporaryFile = null;
  }

  private void fail() {
    if (writer != null) {
      try {
        writer.close();
      } catch (IOException ignored) {
        // Preserve the original output failure.
      }
      writer = null;
    }
    if (temporaryFile != null) {
      try {
        Files.deleteIfExists(temporaryFile);
      } catch (IOException ignored) {
        // An unpublished staging file is safe to leave for manual local cleanup.
      }
      temporaryFile = null;
    }
    state = State.FAILED;
  }

  private void requireState(State expected, OutputSinkFailureStage stage)
      throws OutputSinkException {
    if (state != expected) {
      throw new OutputSinkException(
          new OutputSinkFailure(stage, "sink is " + state + ", expected " + expected), null);
    }
  }

  private OutputSinkException exception(OutputSinkFailureStage stage, Exception exception) {
    String detail = exception.getMessage();
    if (detail == null || detail.isBlank()) {
      detail = exception.getClass().getSimpleName();
    }
    return new OutputSinkException(new OutputSinkFailure(stage, detail), exception);
  }

  private static IOException incompatible(ParquetOutputColumn column, Object value) {
    return new IOException(
        "Parquet column "
            + column.name()
            + " expected "
            + column.type()
            + " but received "
            + value.getClass().getSimpleName());
  }

  private static CompressionCodecName codec(ParquetCompression compression) {
    return switch (compression) {
      case UNCOMPRESSED -> CompressionCodecName.UNCOMPRESSED;
      case SNAPPY -> CompressionCodecName.SNAPPY;
      case ZSTD -> CompressionCodecName.ZSTD;
    };
  }

  private static String schemaText(ParquetOutputConfig config) {
    StringBuilder schema = new StringBuilder("message streamforge {\n");
    for (ParquetOutputColumn column : config.columns()) {
      schema.append(column.required() ? "  required " : "  optional ");
      switch (column.type()) {
        case STRING -> schema.append("binary ").append(column.name()).append(" (STRING)");
        case BOOLEAN -> schema.append("boolean ").append(column.name());
        case INT64 -> schema.append("int64 ").append(column.name());
        case TIMESTAMP_NANOS ->
            schema.append("int64 ").append(column.name()).append(" (TIMESTAMP(NANOS,true))");
        case FIXED_DECIMAL ->
            schema
                .append("binary ")
                .append(column.name())
                .append(" (DECIMAL(19,")
                .append(column.scale().orElseThrow())
                .append("))");
      }
      schema.append(";\n");
    }
    return schema.append("}\n").toString();
  }

  private enum State {
    NEW,
    ACTIVE,
    COMPLETED,
    FAILED
  }
}
