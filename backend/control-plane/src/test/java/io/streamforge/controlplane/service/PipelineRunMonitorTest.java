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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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

  @Test
  void boundsHistoryDeadLettersAndTerminalRunRetention() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-03T00:00:00Z"));
    PipelineRunMonitor bounded = new PipelineRunMonitor(clock);
    UUID observedRun = UUID.randomUUID();
    bounded.register(observedRun, PipelineRunState.RUNNING);
    for (int index = 0; index < 130; index++) {
      bounded.metrics(
          observedRun,
          new PipelineRunMetrics(
              new PipelineCounters(index, index, index, 0, index, 0), 0, 0, 0, 0, 0));
      clock.advance(Duration.ofMillis(100));
    }
    for (int index = 0; index < 60; index++) bounded.deadLetter(observedRun, record());

    assertThat(bounded.snapshot(observedRun).history()).hasSize(PipelineRunMonitor.MAXIMUM_HISTORY);
    assertThat(bounded.snapshot(observedRun).deadLetters())
        .hasSize(PipelineRunMonitor.MAXIMUM_DEAD_LETTERS);

    bounded.state(observedRun, PipelineRunState.COMPLETED, true);
    for (int index = 0; index < PipelineRunMonitor.MAXIMUM_TERMINAL_RUNS; index++) {
      UUID runId = UUID.randomUUID();
      bounded.register(runId, PipelineRunState.RUNNING);
      bounded.state(runId, PipelineRunState.COMPLETED, false);
    }
    assertThat(bounded.observationCount()).isEqualTo(PipelineRunMonitor.MAXIMUM_TERMINAL_RUNS);
  }

  @Test
  void evictsTerminalRunsAfterTheRetentionWindowButKeepsActiveRuns() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-03T00:00:00Z"));
    PipelineRunMonitor bounded = new PipelineRunMonitor(clock);
    UUID terminal = UUID.randomUUID();
    UUID active = UUID.randomUUID();
    bounded.register(terminal, PipelineRunState.RUNNING);
    bounded.state(terminal, PipelineRunState.COMPLETED, false);
    bounded.register(active, PipelineRunState.RUNNING);

    clock.advance(PipelineRunMonitor.TERMINAL_RETENTION.plusSeconds(1));
    bounded.register(UUID.randomUUID(), PipelineRunState.RUNNING);

    assertThat(bounded.hasObservation(terminal)).isFalse();
    assertThat(bounded.hasObservation(active)).isTrue();
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

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
