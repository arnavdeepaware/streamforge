package io.streamforge.pipelineruntime.output;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Common lifecycle and staged-file publication behavior for local output sinks. */
abstract class AbstractOutputSink implements OutputSink {
  private final OutputStream callerStream;
  private final boolean closeCallerStream;
  private final Path destination;
  private State state = State.NEW;
  private OutputStream output;
  private Path temporaryFile;

  AbstractOutputSink(OutputStream callerStream, boolean closeCallerStream) {
    if (callerStream == null) {
      throw new IllegalArgumentException("output stream must not be null");
    }
    this.callerStream = callerStream;
    this.closeCallerStream = closeCallerStream;
    destination = null;
  }

  AbstractOutputSink(Path destination) {
    if (destination == null) {
      throw new IllegalArgumentException("destination must not be null");
    }
    callerStream = null;
    closeCallerStream = false;
    this.destination = destination.toAbsolutePath();
  }

  @Override
  public final void start() throws OutputSinkException {
    requireState(State.NEW, OutputSinkFailureStage.START);
    try {
      if (destination == null) {
        output = new BufferedOutputStream(callerStream);
      } else {
        Path parent = destination.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
          throw new IOException("destination parent directory does not exist: " + parent);
        }
        temporaryFile = Files.createTempFile(parent, ".streamforge-output-", ".tmp");
        output = new BufferedOutputStream(Files.newOutputStream(temporaryFile));
      }
      writePreamble(output);
      state = State.ACTIVE;
    } catch (IOException exception) {
      fail();
      throw exception(OutputSinkFailureStage.START, exception);
    }
  }

  @Override
  public final void write(OutputRecord record) throws OutputSinkException {
    if (record == null) {
      throw new IllegalArgumentException("output record must not be null");
    }
    requireState(State.ACTIVE, OutputSinkFailureStage.WRITE);
    try {
      writeRecord(record, output);
    } catch (IOException exception) {
      fail();
      throw exception(OutputSinkFailureStage.WRITE, exception);
    }
  }

  @Override
  public final void flush() throws OutputSinkException {
    requireState(State.ACTIVE, OutputSinkFailureStage.FLUSH);
    try {
      output.flush();
    } catch (IOException exception) {
      fail();
      throw exception(OutputSinkFailureStage.FLUSH, exception);
    }
  }

  @Override
  public final void complete() throws OutputSinkException {
    requireState(State.ACTIVE, OutputSinkFailureStage.COMPLETE);
    try {
      output.flush();
      if (destination != null || closeCallerStream) {
        output.close();
      }
      output = null;
      if (destination != null) {
        publishTemporaryFile();
      }
      state = State.COMPLETED;
    } catch (IOException exception) {
      fail();
      throw exception(OutputSinkFailureStage.COMPLETE, exception);
    }
  }

  @Override
  public final void abort() {
    if (state == State.ACTIVE || state == State.NEW) {
      fail();
    }
  }

  @Override
  public final void close() {
    if (state == State.ACTIVE || state == State.NEW) {
      abort();
    }
  }

  protected abstract void writePreamble(OutputStream output) throws IOException;

  protected abstract void writeRecord(OutputRecord record, OutputStream output) throws IOException;

  private void publishTemporaryFile() throws IOException {
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

  private void requireState(State expected, OutputSinkFailureStage stage)
      throws OutputSinkException {
    if (state != expected) {
      throw new OutputSinkException(
          new OutputSinkFailure(stage, "sink is " + state + ", expected " + expected), null);
    }
  }

  private OutputSinkException exception(OutputSinkFailureStage stage, IOException exception) {
    String detail = exception.getMessage();
    if (detail == null || detail.isBlank()) {
      detail = exception.getClass().getSimpleName();
    }
    return new OutputSinkException(new OutputSinkFailure(stage, detail), exception);
  }

  private void fail() {
    if (output != null) {
      if (destination != null || closeCallerStream) {
        try {
          output.close();
        } catch (IOException ignored) {
          // The original pipeline failure is more useful than cleanup failure.
        }
      }
      output = null;
    }
    if (temporaryFile != null) {
      try {
        Files.deleteIfExists(temporaryFile);
      } catch (IOException ignored) {
        // The destination is never published from a temporary-file cleanup failure.
      }
      temporaryFile = null;
    }
    state = State.FAILED;
  }

  private enum State {
    NEW,
    ACTIVE,
    COMPLETED,
    FAILED
  }
}
