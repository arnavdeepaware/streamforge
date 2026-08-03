package io.streamforge.pipelineruntime;

import java.util.List;

/** Immutable final outcome from one streaming local pipeline run. */
public record PipelineReport(
    PipelineCounters counters,
    List<PipelineFailure> failures,
    long suppressedFailureCount,
    PipelineOutcome outcome) {
  public PipelineReport {
    if (counters == null || failures == null || suppressedFailureCount < 0 || outcome == null) {
      throw new IllegalArgumentException("pipeline report fields must be present and nonnegative");
    }
    failures = List.copyOf(failures);
  }

  /** Retains the original convenience query for cancellation-aware callers. */
  public boolean cancelled() {
    return outcome == PipelineOutcome.CANCELLED;
  }
}
