package io.streamforge.pipelineruntime.output;

import io.streamforge.common.model.FixedDecimal;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Streaming CSV sink with configured column order and RFC 4180-style escaping. */
public final class CsvOutputSink extends AbstractOutputSink {
  private final CsvOutputConfig config;

  /** Writes to a caller-owned stream that remains open after completion. */
  public CsvOutputSink(OutputStream output, CsvOutputConfig config) {
    super(output, false);
    this.config = requireConfig(config);
  }

  private CsvOutputSink(OutputStream output, CsvOutputConfig config, boolean closeOutput) {
    super(output, closeOutput);
    this.config = requireConfig(config);
  }

  /** Writes to a stream whose ownership is explicitly transferred to this sink. */
  public static CsvOutputSink owning(OutputStream output, CsvOutputConfig config) {
    return new CsvOutputSink(output, config, true);
  }

  /** Writes a finite job to a staged file and replaces the destination on successful completion. */
  public CsvOutputSink(Path destination, CsvOutputConfig config) {
    super(destination);
    this.config = requireConfig(config);
  }

  @Override
  protected void writePreamble(OutputStream output) throws IOException {
    if (!config.includeHeader()) {
      return;
    }
    for (int index = 0; index < config.columns().size(); index++) {
      if (index > 0) {
        output.write(',');
      }
      writeCell(config.columns().get(index).header(), output);
    }
    output.write('\n');
  }

  @Override
  protected void writeRecord(OutputRecord record, OutputStream output) throws IOException {
    for (int index = 0; index < config.columns().size(); index++) {
      if (index > 0) {
        output.write(',');
      }
      Optional<Object> value = valueAt(record.fields(), config.columns().get(index));
      if (value.isPresent()) {
        writeCell(scalar(value.get(), config.columns().get(index)), output);
      }
    }
    output.write('\n');
  }

  private Optional<Object> valueAt(Map<String, Object> fields, CsvOutputColumn column)
      throws IOException {
    Object current = fields;
    for (String segment : column.path().segments()) {
      if (!(current instanceof Map<?, ?> object) || !object.containsKey(segment)) {
        return Optional.empty();
      }
      current = object.get(segment);
    }
    return Optional.of(current);
  }

  private String scalar(Object value, CsvOutputColumn column) throws IOException {
    if (value instanceof String text) {
      return text;
    }
    if (value instanceof Boolean bool) {
      return Boolean.toString(bool);
    }
    if (value instanceof Long integer) {
      return Long.toString(integer);
    }
    if (value instanceof FixedDecimal decimal) {
      return decimal.toString();
    }
    throw new IOException("CSV column " + column.header() + " references a non-scalar value");
  }

  private void writeCell(String value, OutputStream output) throws IOException {
    boolean quoted = false;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == ',' || character == '"' || character == '\n' || character == '\r') {
        quoted = true;
        break;
      }
    }
    if (quoted) {
      output.write('"');
    }
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '"') {
        output.write('"');
      }
      output.write(String.valueOf(character).getBytes(StandardCharsets.UTF_8));
    }
    if (quoted) {
      output.write('"');
    }
  }

  private static CsvOutputConfig requireConfig(CsvOutputConfig config) {
    if (config == null) {
      throw new IllegalArgumentException("CSV output config must not be null");
    }
    return config;
  }
}
