package io.streamforge.controlplane.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.streamforge.controlplane.execution.PipelineRunState;
import io.streamforge.pipelineruntime.PipelineCounters;
import io.streamforge.pipelineruntime.PipelineRunMetrics;
import io.streamforge.pipelineruntime.PipelineStage;
import io.streamforge.pipelineruntime.deadletter.DeadLetterCategory;
import io.streamforge.pipelineruntime.deadletter.DeadLetterPayload;
import io.streamforge.pipelineruntime.deadletter.DeadLetterRecord;
import io.streamforge.pipelineruntime.deadletter.Retryability;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PipelineRunMonitorTest {
  private final PipelineRunMonitor monitor =
      new PipelineRunMonitor(Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void exposesBoundedSafeMetricsAndDeadLetterSnapshots() {
    UUID runId = UUID.randomUUID();
    monitor.register(runId, PipelineRunState.RUNNING);
    monitor.metrics(
        runId, new PipelineRunMetrics(new PipelineCounters(8, 7, 7, 1, 6, 1), 90, 3, 2, 1, 0));
    monitor.deadLetter(runId, record());

    var snapshot = monitor.snapshot(runId);

    assertThat(snapshot.state()).isEqualTo(PipelineRunState.RUNNING);
    assertThat(snapshot.counters().emitted()).isEqualTo(6);
    assertThat(snapshot.latency().averageNanos()).isEqualTo(30);
    assertThat(snapshot.sequenceGapCount()).isEqualTo(2);
    assertThat(snapshot.duplicateCount()).isEqualTo(1);
    assertThat(snapshot.queueDepth()).isZero();
    assertThat(snapshot.history()).hasSize(1);
    assertThat(snapshot.deadLetters())
        .singleElement()
        .satisfies(
            deadLetter -> {
              assertThat(deadLetter.safeMessage()).isEqualTo("invalid price");
              assertThat(deadLetter.payloadPreview()).isEqualTo("bad row");
              assertThat(deadLetter.payloadEncoding()).isEqualTo("utf-8");
            });
  }

  private static DeadLetterRecord record() {
    return DeadLetterRecord.create(
        "monitor-test",
        "1",
        PipelineStage.PARSE,
        "sample.csv:row 2",
        Optional.empty(),
        DeadLetterCategory.MALFORMED_INPUT,
        "invalid price",
        Optional.of(DeadLetterPayload.text("bad row", 64)),
        Instant.parse("2026-08-03T00:00:00Z"),
        Retryability.NON_RETRYABLE,
        "1.0",
        "1.0");
  }
}
