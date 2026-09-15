package io.streamforge.controlplane.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.streamforge.pipelineruntime.PipelineCounters;
import io.streamforge.pipelineruntime.PipelineRunMetrics;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PipelineMetricsTest {
  @Test
  void exportsOnlyBoundedAggregateLabelsAndIncrementalValues() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PipelineMetrics metrics = new PipelineMetrics(registry);
    UUID runId = UUID.randomUUID();

    metrics.started(runId);
    metrics.sample(
        runId, new PipelineRunMetrics(new PipelineCounters(5, 4, 3, 1, 2, 1), 99, 3, 2, 1, 0));
    metrics.sample(
        runId, new PipelineRunMetrics(new PipelineCounters(8, 7, 6, 1, 5, 1), 120, 6, 2, 2, 0));
    metrics.finished(runId, "completed");

    assertThat(registry.get("streamforge.pipeline.active.runs").gauge().value()).isZero();
    assertThat(
            registry.get("streamforge.pipeline.runs").tag("outcome", "completed").counter().count())
        .isEqualTo(1);
    assertThat(
            registry.get("streamforge.pipeline.records").tag("stage", "received").counter().count())
        .isEqualTo(8);
    assertThat(registry.get("streamforge.pipeline.processing.nanoseconds").counter().count())
        .isEqualTo(120);
    assertThat(
            registry
                .get("streamforge.pipeline.sequence.anomalies")
                .tag("kind", "duplicate")
                .counter()
                .count())
        .isEqualTo(2);
    assertThat(registry.getMeters())
        .allSatisfy(
            meter ->
                assertThat(meter.getId().getTags())
                    .allSatisfy(tag -> assertThat(tag.getKey()).isIn("outcome", "stage", "kind")));
  }
}
