package io.streamforge.controlplane.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.streamforge.pipelineruntime.PipelineCounters;
import io.streamforge.pipelineruntime.PipelineRunMetrics;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded-label aggregate metrics for all local pipeline executions. */
final class PipelineMetrics {
  private static final PipelineRunMetrics EMPTY =
      new PipelineRunMetrics(new PipelineCounters(0, 0, 0, 0, 0, 0), 0, 0, 0, 0, 0);

  private final MeterRegistry registry;
  private final AtomicInteger activeRuns = new AtomicInteger();
  private final Map<UUID, PipelineRunMetrics> previous = new ConcurrentHashMap<>();

  PipelineMetrics(MeterRegistry registry) {
    this.registry = registry;
    Gauge.builder("streamforge.pipeline.active.runs", activeRuns, AtomicInteger::get)
        .description("Local pipeline executions currently active")
        .register(registry);
    for (String outcome : new String[] {"completed", "stopped", "failed"}) {
      registry.counter("streamforge.pipeline.runs", "outcome", outcome);
    }
    for (String stage :
        new String[] {"received", "parsed", "normalized", "filtered", "emitted", "failed"}) {
      registry.counter("streamforge.pipeline.records", "stage", stage);
    }
    registry.counter("streamforge.pipeline.processing.nanoseconds");
    registry.counter("streamforge.pipeline.sequence.anomalies", "kind", "gap");
    registry.counter("streamforge.pipeline.sequence.anomalies", "kind", "duplicate");
  }

  void started(UUID runId) {
    if (previous.putIfAbsent(runId, EMPTY) == null) {
      activeRuns.incrementAndGet();
    }
  }

  void sample(UUID runId, PipelineRunMetrics current) {
    previous.computeIfPresent(
        runId,
        (ignored, prior) -> {
          incrementRecords(
              "received", delta(current.counters().received(), prior.counters().received()));
          incrementRecords("parsed", delta(current.counters().parsed(), prior.counters().parsed()));
          incrementRecords(
              "normalized", delta(current.counters().normalized(), prior.counters().normalized()));
          incrementRecords(
              "filtered", delta(current.counters().filtered(), prior.counters().filtered()));
          incrementRecords(
              "emitted", delta(current.counters().emitted(), prior.counters().emitted()));
          incrementRecords("failed", delta(current.counters().failed(), prior.counters().failed()));
          increment(
              registry.counter("streamforge.pipeline.processing.nanoseconds"),
              delta(current.processingNanos(), prior.processingNanos()));
          increment(
              registry.counter("streamforge.pipeline.sequence.anomalies", "kind", "gap"),
              delta(current.sequenceGapCount(), prior.sequenceGapCount()));
          increment(
              registry.counter("streamforge.pipeline.sequence.anomalies", "kind", "duplicate"),
              delta(current.duplicateCount(), prior.duplicateCount()));
          return current;
        });
  }

  void finished(UUID runId, String outcome) {
    if (previous.remove(runId) != null) {
      activeRuns.decrementAndGet();
      registry.counter("streamforge.pipeline.runs", "outcome", outcome).increment();
    }
  }

  private void incrementRecords(String stage, long amount) {
    increment(registry.counter("streamforge.pipeline.records", "stage", stage), amount);
  }

  private static void increment(Counter counter, long amount) {
    if (amount > 0) counter.increment(amount);
  }

  private static long delta(long current, long previous) {
    return current >= previous ? current - previous : current;
  }
}
