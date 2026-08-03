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
import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Keeps bounded operational snapshots without blocking pipeline processing on SSE clients. */
@Service
public class PipelineRunMonitor {
  static final int MAXIMUM_HISTORY = 120;
  static final int MAXIMUM_DEAD_LETTERS = 50;
  static final int MAXIMUM_TERMINAL_RUNS = 100;
  static final Duration TERMINAL_RETENTION = Duration.ofHours(24);
  private static final int MAXIMUM_PAYLOAD_PREVIEW = 4_096;
  private static final Duration MINIMUM_SAMPLE_INTERVAL = Duration.ofMillis(100);

  private final Map<UUID, RunObservation> observations = new ConcurrentHashMap<>();
  private final Map<UUID, CopyOnWriteArraySet<SseEmitter>> subscribers = new ConcurrentHashMap<>();
  private final java.util.Set<UUID> dirty = ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService broadcaster;
  private final Clock clock;

  public PipelineRunMonitor() {
    this(Clock.systemUTC(), true);
  }

  PipelineRunMonitor(Clock clock) {
    this(clock, false);
  }

  private PipelineRunMonitor(Clock clock, boolean scheduleBroadcasts) {
    this.clock = clock;
    broadcaster = Executors.newSingleThreadScheduledExecutor();
    if (scheduleBroadcasts) {
      broadcaster.scheduleWithFixedDelay(this::flushDirty, 100, 100, TimeUnit.MILLISECONDS);
    }
  }

  public synchronized void register(UUID runId, PipelineRunState state) {
    observations.put(runId, new RunObservation(state));
    evictTerminalRuns();
    dirty.add(runId);
  }

  /** Restores a persisted run without fabricating live history. */
  public synchronized void hydrate(
      UUID runId,
      PipelineRunState state,
      PipelineRunMetrics metrics,
      List<DeadLetterResponse> deadLetters,
      boolean outputAvailable) {
    RunObservation observation =
        observations.computeIfAbsent(runId, ignored -> new RunObservation(state));
    observation.state = state;
    observation.metrics = metrics;
    observation.outputAvailable = outputAvailable;
    observation.deadLetters.clear();
    deadLetters.stream().limit(MAXIMUM_DEAD_LETTERS).forEach(observation.deadLetters::addLast);
    if (!state.active() && observation.terminalAt == null) observation.terminalAt = clock.instant();
    evictTerminalRuns();
  }

  public synchronized void state(UUID runId, PipelineRunState state, boolean outputAvailable) {
    RunObservation observation = observation(runId);
    observation.state = state;
    observation.outputAvailable = outputAvailable;
    if (!state.active()) {
      observation.terminalAt = clock.instant();
      appendSample(observation, observation.terminalAt);
      evictTerminalRuns();
    }
    dirty.add(runId);
  }

  public synchronized void metrics(UUID runId, PipelineRunMetrics metrics) {
    RunObservation observation = observation(runId);
    observation.metrics = metrics;
    Instant now = clock.instant();
    if (observation.history.isEmpty()
        || Duration.between(observation.history.getLast().timestamp(), now)
                .compareTo(MINIMUM_SAMPLE_INTERVAL)
            >= 0) {
      appendSample(observation, now);
      dirty.add(runId);
    }
  }

  public synchronized void deadLetter(UUID runId, DeadLetterRecord record) {
    RunObservation observation = observation(runId);
    observation.deadLetters.addFirst(toResponse(record));
    while (observation.deadLetters.size() > MAXIMUM_DEAD_LETTERS) {
      observation.deadLetters.removeLast();
    }
    dirty.add(runId);
  }

  public SseEmitter subscribe(UUID runId) {
    SseEmitter emitter = new SseEmitter(0L);
    subscribers.computeIfAbsent(runId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
    emitter.onCompletion(() -> removeSubscriber(runId, emitter));
    emitter.onTimeout(() -> removeSubscriber(runId, emitter));
    send(runId, emitter);
    return emitter;
  }

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
        observation.outputAvailable,
        List.copyOf(observation.history),
        List.copyOf(observation.deadLetters));
  }

  public boolean hasObservation(UUID runId) {
    return observations.containsKey(runId);
  }

  int observationCount() {
    return observations.size();
  }

  private RunObservation observation(UUID runId) {
    return observations.computeIfAbsent(
        runId, ignored -> new RunObservation(PipelineRunState.CREATED));
  }

  private void flushDirty() {
    try {
      evictTerminalRuns();
      List<UUID> pending = new ArrayList<>(dirty);
      pending.forEach(dirty::remove);
      pending.forEach(this::broadcast);
    } catch (RuntimeException ignored) {
      // A monitoring transport failure must not terminate the bounded broadcaster.
    }
  }

  private void broadcast(UUID runId) {
    CopyOnWriteArraySet<SseEmitter> runSubscribers = subscribers.get(runId);
    if (runSubscribers == null) return;
    for (SseEmitter emitter : runSubscribers) send(runId, emitter);
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

  private synchronized void evictTerminalRuns() {
    Instant cutoff = clock.instant().minus(TERMINAL_RETENTION);
    observations
        .entrySet()
        .removeIf(
            entry ->
                entry.getValue().terminalAt != null
                    && entry.getValue().terminalAt.isBefore(cutoff)
                    && !subscribers.containsKey(entry.getKey()));
    List<Map.Entry<UUID, RunObservation>> terminal =
        observations.entrySet().stream()
            .filter(entry -> entry.getValue().terminalAt != null)
            .sorted(Comparator.comparing(entry -> entry.getValue().terminalAt))
            .toList();
    int excess = terminal.size() - MAXIMUM_TERMINAL_RUNS;
    for (int index = 0; index < excess; index++) {
      UUID runId = terminal.get(index).getKey();
      if (!subscribers.containsKey(runId)) observations.remove(runId);
    }
  }

  private static PipelineMonitoringResponse empty(UUID runId, PipelineRunState state) {
    return new PipelineMonitoringResponse(
        runId,
        state,
        new PipelineCountersResponse(0, 0, 0, 0, 0),
        0,
        new ProcessingLatencyResponse(0, 0, 0),
        0,
        0,
        0,
        false,
        List.of(),
        List.of());
  }

  private static long rate(Deque<MetricSampleResponse> history) {
    if (history.size() < 2) return 0;
    MetricSampleResponse first = history.getFirst();
    MetricSampleResponse last = history.getLast();
    long elapsedNanos = Duration.between(first.timestamp(), last.timestamp()).toNanos();
    if (elapsedNanos <= 0 || last.received() < first.received()) return 0;
    return BigInteger.valueOf(last.received() - first.received())
        .multiply(BigInteger.valueOf(1_000_000_000L))
        .divide(BigInteger.valueOf(elapsedNanos))
        .min(BigInteger.valueOf(Long.MAX_VALUE))
        .longValue();
  }

  private static void appendSample(RunObservation observation, Instant timestamp) {
    PipelineCounters counters = observation.metrics.counters();
    observation.history.addLast(
        new MetricSampleResponse(
            timestamp, counters.received(), counters.emitted(), counters.failed()));
    while (observation.history.size() > MAXIMUM_HISTORY) observation.history.removeFirst();
  }

  static DeadLetterResponse toResponse(DeadLetterRecord record) {
    String encoding = null;
    String preview = null;
    boolean truncated = false;
    if (record.payload().isPresent()) {
      DeadLetterPayload payload = record.payload().orElseThrow();
      encoding = payload.encoding();
      preview = payload.value();
      truncated = payload.truncated() || preview.length() > MAXIMUM_PAYLOAD_PREVIEW;
      if (preview.length() > MAXIMUM_PAYLOAD_PREVIEW) {
        preview = preview.substring(0, MAXIMUM_PAYLOAD_PREVIEW);
      }
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

  @PreDestroy
  public void close() {
    broadcaster.shutdownNow();
  }

  private static final class RunObservation {
    private volatile PipelineRunState state;
    private PipelineRunMetrics metrics =
        new PipelineRunMetrics(new PipelineCounters(0, 0, 0, 0, 0, 0), 0, 0, 0, 0, 0);
    private final Deque<MetricSampleResponse> history = new ArrayDeque<>();
    private final Deque<DeadLetterResponse> deadLetters = new ArrayDeque<>();
    private Instant terminalAt;
    private boolean outputAvailable;

    private RunObservation(PipelineRunState state) {
      this.state = state;
    }
  }
}
