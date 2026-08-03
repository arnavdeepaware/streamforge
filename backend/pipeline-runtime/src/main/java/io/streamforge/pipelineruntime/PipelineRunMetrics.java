package io.streamforge.pipelineruntime;

/** Immutable live snapshot emitted by a local run without retaining event payloads. */
public record PipelineRunMetrics(
    PipelineCounters counters,
    long processingNanos,
    long processedEventCount,
    long sequenceGapCount,
    long duplicateCount,
    long queueDepth) {
  public PipelineRunMetrics {
    if (counters == null
        || processingNanos < 0
        || processedEventCount < 0
        || sequenceGapCount < 0
        || duplicateCount < 0
        || queueDepth < 0) {
      throw new IllegalArgumentException("pipeline live metrics must be present and nonnegative");
    }
  }

  /** Returns the integer average per canonical event without a floating-point conversion. */
  public long averageProcessingNanos() {
    return processedEventCount == 0 ? 0 : processingNanos / processedEventCount;
  }
}
