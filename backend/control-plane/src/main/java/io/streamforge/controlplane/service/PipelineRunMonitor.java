package io.streamforge.controlplane.service;

import io.streamforge.controlplane.api.DeadLetterResponse;
import io.streamforge.controlplane.api.MetricSampleResponse;
import io.streamforge.controlplane.api.PipelineCountersResponse;
import io.streamforge.controlplane.api.PipelineMonitoringResponse;
import io.streamforge.controlplane.api.ProcessingLatencyResponse;
import io.streamforge.controlplane.execution.PipelineRunState;
import io.streamforge.pipelineruntime.PipelineCounters;
import io.streamforge.pipelineruntime.PipelineRunMetrics;
import io.streamforge.pipelineruntime.deadletter.DeadLetterPayload;
import io.streamforge.pipelineruntime.deadletter.DeadLetterRecord;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Keeps a deliberately bounded, in-memory operational view for active and recently finished local
 * pipeline runs. Durable lifecycle and final reports remain owned by PostgreSQL.
 */
@Service
public class PipelineRunMonitor {
  static final int MAXIMUM_HISTORY = 120;
  static final int MAXIMUM_DEAD_LETTERS = 50;
  private static final int MAXIMUM_PAYLOAD_PREVIEW = 4_096;
  private static final Duration MINIMUM_SAMPLE_INTERVAL = Duration.ofMillis(100);

  private final Map<UUID, RunObservation> observations = new ConcurrentHashMap<>();
  private final Map<UUID, CopyOnWriteArraySet<SseEmitter>> subscribers = new ConcurrentHashMap<>();
  private final Clock clock;

  public PipelineRunMonitor() {
    this(Clock.systemUTC());
  }

  PipelineRunMonitor(Clock clock) {
    this.clock = clock;
  }

  /** Starts a bounded monitoring session before a local execution begins. */
  public void register(UUID runId, PipelineRunState state) {
    observations.put(runId, new RunObservation(state));
    broadcast(runId);
  }

  /** Updates the state carried by the next monitoring snapshot. */
  public synchronized void state(UUID runId, PipelineRunState state) {
    RunObservation observation = observation(runId);
    observation.state = state;
    if (!state.active()) appendSample(observation, clock.instant());
    broadcast(runId);
  }

  /** Stores one runtime metrics snapshot and caps the rate-history ring buffer. */
  public synchronized void metrics(UUID runId, PipelineRunMetrics metrics) {
    RunObservation observation = observation(runId);
    observation.metrics = metrics;
    Instant now = clock.instant();
    if (observation.history.isEmpty()
        || Duration.between(observation.history.getLast().timestamp(), now)
                .compareTo(MINIMUM_SAMPLE_INTERVAL)
            >= 0) {
      appendSample(observation, now);
      broadcast(runId);
    }
  }

  /** Stores a safe dead-letter summary and caps retained payload previews. */
  public synchronized void deadLetter(UUID runId, DeadLetterRecord record) {
    RunObservation observation = observation(runId);
    observation.deadLetters.addFirst(toResponse(record));
    while (observation.deadLetters.size() > MAXIMUM_DEAD_LETTERS) {
      observation.deadLetters.removeLast();
    }
    broadcast(runId);
  }

  /** Opens a monitoring stream that emits the latest bounded snapshot immediately. */
  public SseEmitter subscribe(UUID runId) {
    SseEmitter emitter = new SseEmitter(0L);
    subscribers.computeIfAbsent(runId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
    emitter.onCompletion(() -> removeSubscriber(runId, emitter));
    emitter.onTimeout(() -> removeSubscriber(runId, emitter));
    send(runId, emitter);
    return emitter;
  }

  /** Returns the current bounded monitoring snapshot without reading raw pipeline payloads. */
  public synchronized PipelineMonitoringResponse snapshot(UUID runId) {
    RunObservation observation = observations.get(runId);
    if (observation == null) return empty(runId, PipelineRunState.CREATED);
    PipelineRunMetrics metrics = observation.metrics;
    PipelineCounters counters = metrics.counters();
    return new PipelineMonitoringResponse(
        runId,
        observation.state,
        PipelineCountersResponse.from(counters),
        rate(observation.history),
        new ProcessingLatencyResponse(
            metrics.processingNanos(),
            metrics.processedEventCount(),
            metrics.averageProcessingNanos()),
        metrics.queueDepth(),
        metrics.sequenceGapCount(),
        metrics.duplicateCount(),
        List.copyOf(observation.history),
        List.copyOf(observation.deadLetters));
  }

  private RunObservation observation(UUID runId) {
    return observations.computeIfAbsent(runId, id -> new RunObservation(PipelineRunState.CREATED));
  }

  private void broadcast(UUID runId) {
    for (SseEmitter emitter : subscribers.getOrDefault(runId, new CopyOnWriteArraySet<>())) {
      send(runId, emitter);
    }
  }

  private void send(UUID runId, SseEmitter emitter) {
    try {
      PipelineMonitoringResponse snapshot = snapshot(runId);
      emitter.send(SseEmitter.event().name("pipeline-health").data(snapshot));
      if (!snapshot.state().active()) emitter.complete();
    } catch (Exception exception) {
      emitter.completeWithError(exception);
      removeSubscriber(runId, emitter);
    }
  }

  private void removeSubscriber(UUID runId, SseEmitter emitter) {
    CopyOnWriteArraySet<SseEmitter> runSubscribers = subscribers.get(runId);
    if (runSubscribers == null) return;
    runSubscribers.remove(emitter);
    if (runSubscribers.isEmpty()) subscribers.remove(runId, runSubscribers);
  }

  private static PipelineMonitoringResponse empty(UUID runId, PipelineRunState state) {
    PipelineCountersResponse counters = new PipelineCountersResponse(0, 0, 0, 0, 0);
    return new PipelineMonitoringResponse(
        runId,
        state,
        counters,
        0,
        new ProcessingLatencyResponse(0, 0, 0),
        0,
        0,
        0,
        List.of(),
        List.of());
  }

  private static long rate(Deque<MetricSampleResponse> history) {
    if (history.size() < 2) return 0;
    MetricSampleResponse first = history.getFirst();
    MetricSampleResponse last = history.getLast();
    long elapsedNanos = java.time.Duration.between(first.timestamp(), last.timestamp()).toNanos();
    if (elapsedNanos <= 0 || last.received() < first.received()) return 0;
    BigInteger rate =
        BigInteger.valueOf(last.received() - first.received())
            .multiply(BigInteger.valueOf(1_000_000_000L))
            .divide(BigInteger.valueOf(elapsedNanos));
    return rate.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
  }

  private static void appendSample(RunObservation observation, Instant timestamp) {
    PipelineCounters counters = observation.metrics.counters();
    observation.history.addLast(
        new MetricSampleResponse(
            timestamp, counters.received(), counters.emitted(), counters.failed()));
    while (observation.history.size() > MAXIMUM_HISTORY) observation.history.removeFirst();
  }

  private static DeadLetterResponse toResponse(DeadLetterRecord record) {
    String encoding = null;
    String preview = null;
    boolean truncated = false;
    if (record.payload().isPresent()) {
      DeadLetterPayload payload = record.payload().orElseThrow();
      encoding = payload.encoding();
      preview = payload.value();
      truncated = payload.truncated() || preview.length() > MAXIMUM_PAYLOAD_PREVIEW;
      if (preview.length() > MAXIMUM_PAYLOAD_PREVIEW)
        preview = preview.substring(0, MAXIMUM_PAYLOAD_PREVIEW);
    }
    return new DeadLetterResponse(
        record.failureId(),
        record.stage().name(),
        record.category().name(),
        record.sourceLocation(),
        record.safeMessage(),
        record.retryability().name(),
        record.timestamp(),
        encoding,
        preview,
        truncated);
  }

  private static final class RunObservation {
    private volatile PipelineRunState state;
    private PipelineRunMetrics metrics =
        new PipelineRunMetrics(new PipelineCounters(0, 0, 0, 0, 0, 0), 0, 0, 0, 0, 0);
    private final Deque<MetricSampleResponse> history = new ArrayDeque<>();
    private final Deque<DeadLetterResponse> deadLetters = new ArrayDeque<>();

    private RunObservation(PipelineRunState state) {
      this.state = state;
    }
  }
}
