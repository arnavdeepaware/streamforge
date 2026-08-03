package io.streamforge.pipelineruntime.output;

import io.streamforge.common.model.FixedDecimal;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Streaming JSON Lines sink with deterministic object ordering and exact numeric output. */
public final class JsonLinesOutputSink extends AbstractOutputSink {
  /** Writes to a caller-owned stream that remains open after completion. */
  public JsonLinesOutputSink(OutputStream output) {
    super(output, false);
  }

  private JsonLinesOutputSink(OutputStream output, boolean closeOutput) {
    super(output, closeOutput);
  }

  /** Writes to a stream whose ownership is explicitly transferred to this sink. */
  public static JsonLinesOutputSink owning(OutputStream output) {
    return new JsonLinesOutputSink(output, true);
  }

  /** Writes a finite job to a staged file and replaces the destination on successful completion. */
  public JsonLinesOutputSink(Path destination) {
    super(destination);
  }

  @Override
  protected void writePreamble(OutputStream output) {
    // JSON Lines has no header.
  }

  @Override
  protected void writeRecord(OutputRecord record, OutputStream output) throws IOException {
    writeValue(record.fields(), output);
    output.write('\n');
  }

  private void writeValue(Object value, OutputStream output) throws IOException {
    if (value instanceof String text) {
      writeString(text, output);
    } else if (value instanceof Boolean bool) {
      writeAscii(bool ? "true" : "false", output);
    } else if (value instanceof Long integer) {
      writeAscii(Long.toString(integer), output);
    } else if (value instanceof FixedDecimal decimal) {
      writeAscii(decimal.toString(), output);
    } else if (value instanceof Map<?, ?> object) {
      writeObject(object, output);
    } else if (value instanceof List<?> array) {
      writeArray(array, output);
    } else {
      throw new IOException("unsupported output value type");
    }
  }

  private void writeObject(Map<?, ?> object, OutputStream output) throws IOException {
    output.write('{');
    boolean first = true;
    for (Map.Entry<?, ?> entry : object.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new IOException("output object keys must be strings");
      }
      if (!first) {
        output.write(',');
      }
      writeString(key, output);
      output.write(':');
      writeValue(entry.getValue(), output);
      first = false;
    }
    output.write('}');
  }

  private void writeArray(List<?> array, OutputStream output) throws IOException {
    output.write('[');
    for (int index = 0; index < array.size(); index++) {
      if (index > 0) {
        output.write(',');
      }
      writeValue(array.get(index), output);
    }
    output.write(']');
  }

  private void writeString(String value, OutputStream output) throws IOException {
    output.write('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '"' -> writeAscii("\\\"", output);
        case '\\' -> writeAscii("\\\\", output);
        case '\b' -> writeAscii("\\b", output);
        case '\f' -> writeAscii("\\f", output);
        case '\n' -> writeAscii("\\n", output);
        case '\r' -> writeAscii("\\r", output);
        case '\t' -> writeAscii("\\t", output);
        default -> {
          if (character < 0x20) {
            writeAscii(String.format("\\u%04x", (int) character), output);
          } else {
            writeAscii(String.valueOf(character), output);
          }
        }
      }
    }
    output.write('"');
  }

  private void writeAscii(String value, OutputStream output) throws IOException {
    output.write(value.getBytes(StandardCharsets.UTF_8));
  }
}
