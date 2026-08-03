package io.streamforge.pipelineruntime;

import java.util.List;

/** Immutable final outcome from one streaming local pipeline run. */
public record PipelineReport(
    PipelineCounters counters,
    List<PipelineFailure> failures,
    long suppressedFailureCount,
    boolean cancelled) {
  public PipelineReport {
    if (counters == null || failures == null || suppressedFailureCount < 0) {
      throw new IllegalArgumentException("pipeline report fields must be present and nonnegative");
    }
    failures = List.copyOf(failures);
  }
}
