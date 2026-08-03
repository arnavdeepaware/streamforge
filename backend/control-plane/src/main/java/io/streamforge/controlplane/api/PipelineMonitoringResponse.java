package io.streamforge.controlplane.api;

import io.streamforge.controlplane.execution.PipelineRunState;
import java.util.List;
import java.util.UUID;

/** Bounded live health snapshot for one local pipeline run. */
public record PipelineMonitoringResponse(
    UUID runId,
    PipelineRunState state,
    PipelineCountersResponse counters,
    long eventRatePerSecond,
    ProcessingLatencyResponse latency,
    long queueDepth,
    long sequenceGapCount,
    long duplicateCount,
    List<MetricSampleResponse> history,
    List<DeadLetterResponse> deadLetters) {
  public PipelineMonitoringResponse {
    history = List.copyOf(history);
    deadLetters = List.copyOf(deadLetters);
  }
}
