package io.streamforge.pipelineruntime.output;

/**
 * Incremental destination for validated output records.
 *
 * <p>Call {@link #start()}, then zero or more {@link #write(OutputRecord)} calls, and finally
 * {@link #complete()}. {@link #flush()} is explicit so events are not flushed individually. Call
 * {@link #abort()} after a failure to discard an incomplete finite file output.
 *
 * <p>File sinks write to a sibling temporary file and publish it on completion with an atomic move
 * where the file system supports one. A sink failure deletes that temporary file and leaves the
 * prior destination unchanged. A caller-provided stream can contain a partial record after an I/O
 * failure; it remains open unless ownership was explicitly transferred, and the failed sink cannot
 * accept further records.
 */
public interface OutputSink extends AutoCloseable {

  /** Opens the sink and writes any format preamble. */
  void start() throws OutputSinkException;

  /** Writes one complete output record in source call order. */
  void write(OutputRecord record) throws OutputSinkException;

  /** Deliberately flushes bytes already accepted by this sink. */
  void flush() throws OutputSinkException;

  /** Flushes, finalizes, and publishes a successfully completed finite output. */
  void complete() throws OutputSinkException;

  /** Abandons an active output. This never closes a caller-owned stream. */
  void abort();

  /** Aborts an active sink when callers leave its lifecycle early. */
  @Override
  void close();
}
