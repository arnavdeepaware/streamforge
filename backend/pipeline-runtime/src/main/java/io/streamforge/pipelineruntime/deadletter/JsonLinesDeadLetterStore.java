package io.streamforge.pipelineruntime.deadletter;

import io.streamforge.pipelineruntime.output.JsonLinesOutputSink;
import io.streamforge.pipelineruntime.output.OutputRecord;
import io.streamforge.pipelineruntime.output.OutputSinkException;
import java.nio.file.Path;

/** Staged JSON Lines storage for durable local dead-letter records. */
public final class JsonLinesDeadLetterStore implements AutoCloseable {
  private final JsonLinesOutputSink sink;

  public JsonLinesDeadLetterStore(Path path) {
    if (path == null) {
      throw new IllegalArgumentException("dead-letter path must not be null");
    }
    sink = new JsonLinesOutputSink(path);
  }

  public void start() throws OutputSinkException {
    sink.start();
  }

  public void write(DeadLetterRecord record) throws OutputSinkException {
    sink.write(new OutputRecord(record.asJson()));
  }

  public void complete() throws OutputSinkException {
    sink.complete();
  }

  public void abort() {
    sink.abort();
  }

  @Override
  public void close() {
    sink.close();
  }
}
