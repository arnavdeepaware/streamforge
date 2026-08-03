package io.streamforge.controlplane.api;

import java.time.Instant;

/** One bounded monitoring sample retained for the dashboard's rate history. */
public record MetricSampleResponse(Instant timestamp, long received, long emitted, long failed) {}
