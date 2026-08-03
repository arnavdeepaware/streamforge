package io.streamforge.pipelineruntime;

import java.util.concurrent.atomic.AtomicBoolean;

/** Thread-safe cancellation signal for a streaming local pipeline run. */
public final class PipelineCancellation {
  private final AtomicBoolean cancelled = new AtomicBoolean();

  /** Requests cancellation; the runner stops before accepting the next source event. */
  public void cancel() {
    cancelled.set(true);
  }

  /** Returns whether cancellation was requested. */
  public boolean isCancelled() {
    return cancelled.get();
  }
}
