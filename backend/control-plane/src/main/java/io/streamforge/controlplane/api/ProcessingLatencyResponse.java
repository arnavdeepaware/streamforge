package io.streamforge.controlplane.api;

/** Exact integer-nanosecond processing latency summary for a pipeline run. */
public record ProcessingLatencyResponse(long totalNanos, long processedEvents, long averageNanos) {}
