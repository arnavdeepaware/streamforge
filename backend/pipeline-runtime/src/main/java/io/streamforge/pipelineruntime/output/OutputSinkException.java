package io.streamforge.pipelineruntime.output;

/** Checked pipeline failure raised when an output sink cannot safely continue. */
public final class OutputSinkException extends Exception {
  private final OutputSinkFailure failure;

  OutputSinkException(OutputSinkFailure failure, Throwable cause) {
    super(failure.detail(), cause);
    this.failure = failure;
  }

  /** Returns the stable stage and diagnostic detail for this failure. */
  public OutputSinkFailure failure() {
    return failure;
  }
}
