package io.streamforge.controlplane.api;

import io.streamforge.pipelineruntime.PipelineCounters;

/** Read-only nonnegative pipeline event counters. */
public record PipelineCountersResponse(
    long received, long parsed, long emitted, long filtered, long failed) {
  public static PipelineCountersResponse from(PipelineCounters counters) {
    return new PipelineCountersResponse(
        counters.received(),
        counters.parsed(),
        counters.emitted(),
        counters.filtered(),
        counters.failed());
  }
}
