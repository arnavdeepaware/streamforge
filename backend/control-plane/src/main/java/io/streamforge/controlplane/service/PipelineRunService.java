package io.streamforge.controlplane.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.streamforge.controlplane.api.DeadLetterResponse;
import io.streamforge.controlplane.api.PipelineMonitoringResponse;
import io.streamforge.controlplane.api.PipelineReportResponse;
import io.streamforge.controlplane.api.PipelineRunResponse;
import io.streamforge.controlplane.api.StartPipelineRequest;
import io.streamforge.controlplane.execution.PipelineExecutionBackend;
import io.streamforge.controlplane.execution.PipelineExecutionHandle;
import io.streamforge.controlplane.execution.PipelineExecutionListener;
import io.streamforge.controlplane.execution.PipelineExecutionResult;
import io.streamforge.controlplane.execution.PipelineRunState;
import io.streamforge.controlplane.service.PipelineRunPersistenceService.PreparedRun;
import io.streamforge.controlplane.service.PipelineRunPersistenceService.StoredRun;
import io.streamforge.pipelineruntime.PipelineCounters;
import io.streamforge.pipelineruntime.PipelineOutcome;
import io.streamforge.pipelineruntime.PipelineRunMetrics;
import io.streamforge.pipelineruntime.deadletter.DeadLetterRecord;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Coordinates committed lifecycle state with one asynchronous execution backend. */
@Service
public class PipelineRunService {
  private final PipelineRunPersistenceService persistence;
  private final PipelineExecutionBackend backend;
  private final PipelineRunMonitor monitor;
  private final ObjectMapper mapper;
  private final Path artifactRoot;
  private final ConcurrentHashMap<UUID, ActiveRun> active = new ConcurrentHashMap<>();
  private final Counter started;
  private final Counter completed;
  private final Counter failed;

  public PipelineRunService(
      PipelineRunPersistenceService persistence,
      PipelineExecutionBackend backend,
      PipelineRunMonitor monitor,
      ObjectMapper mapper,
      MeterRegistry metrics,
      @Value("${streamforge.local-pipeline.artifact-root:.streamforge/artifacts}")
          String artifactRoot) {
    this.persistence = persistence;
    this.backend = backend;
    this.monitor = monitor;
    this.mapper = mapper;
    this.artifactRoot = Path.of(artifactRoot).toAbsolutePath().normalize();
    started = metrics.counter("streamforge.pipeline.runs", "outcome", "started");
    completed = metrics.counter("streamforge.pipeline.runs", "outcome", "completed");
    failed = metrics.counter("streamforge.pipeline.runs", "outcome", "failed");
  }

  /** Starts the latest revision after committing its STARTING lifecycle state. */
  public PipelineRunResponse start(UUID pipelineId, StartPipelineRequest request) {
    ActiveRun activeRun = new ActiveRun();
    if (active.putIfAbsent(pipelineId, activeRun) != null) {
      throw new IllegalStateException("pipeline already has an active run");
    }
    PreparedRun prepared = null;
    try {
      prepared =
          persistence.createStarting(pipelineId, request == null ? null : request.deadLetter());
      UUID runId = prepared.response().runId();
      activeRun.assign(runId);
      monitor.register(runId, prepared.response().state());
      PipelineExecutionHandle handle =
          backend.start(prepared.command(), new Listener(runId, pipelineId, activeRun));
      activeRun.attach(handle);
      started.increment();
      return prepared.response();
    } catch (RuntimeException exception) {
      try {
        if (prepared != null) {
          PipelineRunResponse response = persistence.fail(prepared.response().runId(), exception);
          monitor.state(response.runId(), response.state(), false);
        }
      } catch (RuntimeException persistenceFailure) {
        exception.addSuppressed(persistenceFailure);
      } finally {
        active.remove(pipelineId, activeRun);
      }
      throw exception;
    }
  }

  public PipelineRunResponse stop(UUID pipelineId, UUID runId) {
    ActiveRun activeRun = active.get(pipelineId);
    if (activeRun == null || !activeRun.matches(runId)) {
      throw new IllegalStateException("pipeline run has no active execution handle");
    }
    PipelineRunResponse response = persistence.requestStop(pipelineId, runId);
    monitor.state(runId, response.state(), false);
    activeRun.cancel();
    return response;
  }

  public PipelineRunResponse status(UUID pipelineId, UUID runId) {
    return persistence.get(pipelineId, runId).response();
  }

  public Optional<PipelineRunResponse> latest(UUID pipelineId) {
    return persistence.latest(pipelineId).map(StoredRun::response);
  }

  public PipelineReportResponse report(UUID pipelineId, UUID runId) {
    return persistence.report(pipelineId, runId);
  }

  public PipelineMonitoringResponse monitoring(UUID pipelineId, UUID runId) {
    StoredRun run = persistence.get(pipelineId, runId);
    ensureHydrated(run);
    return monitor.snapshot(run.response().runId());
  }

  public SseEmitter events(UUID pipelineId, UUID runId) {
    StoredRun run = persistence.get(pipelineId, runId);
    ensureHydrated(run);
    return monitor.subscribe(run.response().runId());
  }

  public Resource output(UUID pipelineId, UUID runId) {
    StoredRun run = persistence.get(pipelineId, runId);
    if (run.response().state() != PipelineRunState.COMPLETED || run.outputArtifactPath() == null) {
      throw new IllegalStateException("pipeline output is not available for this run");
    }
    return new FileSystemResource(resolveArtifact(run.outputArtifactPath()));
  }

  @EventListener(ApplicationReadyEvent.class)
  public void reconcileInterruptedRuns() {
    persistence
        .reconcileInterrupted()
        .forEach(run -> monitor.state(run.runId(), run.state(), false));
  }

  private void ensureHydrated(StoredRun run) {
    UUID runId = run.response().runId();
    if (monitor.hasObservation(runId)) return;
    monitor.hydrate(
        runId,
        run.response().state(),
        metrics(run.finalReport()),
        restoredDeadLetters(run.deadLetterArtifactPath()),
        outputAvailable(run));
  }

  private PipelineRunMetrics metrics(JsonNode report) {
    if (report == null || !report.path("counters").isObject()) return emptyMetrics();
    JsonNode counters = report.path("counters");
    try {
      return new PipelineRunMetrics(
          new PipelineCounters(
              exactLong(counters, "received"),
              exactLong(counters, "parsed"),
              exactLong(counters, "normalized"),
              exactLong(counters, "filtered"),
              exactLong(counters, "emitted"),
              exactLong(counters, "failed")),
          0,
          0,
          0,
          0,
          0);
    } catch (IllegalArgumentException exception) {
      return emptyMetrics();
    }
  }

  private List<DeadLetterResponse> restoredDeadLetters(String relativePath) {
    if (relativePath == null) return List.of();
    Path file;
    try {
      file = resolveArtifact(relativePath);
    } catch (RuntimeException exception) {
      return List.of();
    }
    Deque<DeadLetterResponse> retained = new ArrayDeque<>();
    try (BufferedReader reader = Files.newBufferedReader(file)) {
      String line;
      while ((line = reader.readLine()) != null) {
        DeadLetterResponse response = deadLetter(mapper.readTree(line));
        retained.addFirst(response);
        while (retained.size() > PipelineRunMonitor.MAXIMUM_DEAD_LETTERS) {
          retained.removeLast();
        }
      }
      return List.copyOf(retained);
    } catch (IOException | IllegalArgumentException exception) {
      return List.of();
    }
  }

  private DeadLetterResponse deadLetter(JsonNode node) {
    JsonNode payload = node.path("payload");
    return new DeadLetterResponse(
        requiredText(node, "failureId"),
        requiredText(node, "stage"),
        requiredText(node, "category"),
        requiredText(node, "sourceLocation"),
        requiredText(node, "safeMessage"),
        requiredText(node, "retryability"),
        Instant.parse(requiredText(node, "timestamp")),
        payload.isObject() ? requiredText(payload, "encoding") : null,
        payload.isObject() ? requiredText(payload, "value") : null,
        payload.isObject() && payload.path("truncated").asBoolean(false));
  }

  private boolean outputAvailable(StoredRun run) {
    if (run.response().state() != PipelineRunState.COMPLETED || run.outputArtifactPath() == null)
      return false;
    try {
      return Files.isRegularFile(resolveArtifact(run.outputArtifactPath()));
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private Path resolveArtifact(String relativePath) {
    try {
      Files.createDirectories(artifactRoot);
      Path root = artifactRoot.toRealPath();
      Path relative = Path.of(relativePath);
      if (relative.isAbsolute() || relative.normalize().startsWith("..")) {
        throw new IllegalStateException("stored artifact path is outside the managed root");
      }
      Path file = root.resolve(relative).normalize().toRealPath();
      if (!file.startsWith(root) || !Files.isRegularFile(file)) {
        throw new NoSuchElementException("pipeline output is not available");
      }
      return file;
    } catch (InvalidPathException | IOException exception) {
      throw new NoSuchElementException("pipeline output is not available", exception);
    }
  }

  private static long exactLong(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (!value.isIntegralNumber() || !value.canConvertToLong()) {
      throw new IllegalArgumentException("invalid persisted counter");
    }
    return value.longValue();
  }

  private static String requiredText(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (!value.isTextual()) throw new IllegalArgumentException("invalid dead-letter record");
    return value.textValue();
  }

  private static PipelineRunMetrics emptyMetrics() {
    return new PipelineRunMetrics(new PipelineCounters(0, 0, 0, 0, 0, 0), 0, 0, 0, 0, 0);
  }

  private final class Listener implements PipelineExecutionListener {
    private final UUID runId;
    private final UUID pipelineId;
    private final ActiveRun activeRun;

    private Listener(UUID runId, UUID pipelineId, ActiveRun activeRun) {
      this.runId = runId;
      this.pipelineId = pipelineId;
      this.activeRun = activeRun;
    }

    @Override
    public void onRunning() {
      PipelineRunResponse response = persistence.running(runId);
      monitor.state(runId, response.state(), false);
    }

    @Override
    public void onCompleted(PipelineExecutionResult result) {
      try {
        PipelineRunResponse response = persistence.finish(runId, result);
        boolean outputAvailable =
            response.state() == PipelineRunState.COMPLETED
                && result.report().outcome() == PipelineOutcome.COMPLETED
                && result.outputArtifactPath().isPresent();
        monitor.state(runId, response.state(), outputAvailable);
        if (response.state() == PipelineRunState.FAILED) failed.increment();
        else completed.increment();
      } finally {
        completeActive();
      }
    }

    @Override
    public void onFailed(Throwable failure) {
      try {
        PipelineRunResponse response = persistence.fail(runId, failure);
        monitor.state(runId, response.state(), false);
        failed.increment();
      } finally {
        completeActive();
      }
    }

    @Override
    public void onMetrics(PipelineRunMetrics metrics) {
      monitor.metrics(runId, metrics);
    }

    @Override
    public void onDeadLetter(DeadLetterRecord record) {
      monitor.deadLetter(runId, record);
    }

    private void completeActive() {
      activeRun.complete();
      active.remove(pipelineId, activeRun);
    }
  }

  private static final class ActiveRun {
    private UUID runId;
    private PipelineExecutionHandle handle;
    private boolean cancelRequested;
    private boolean complete;

    private synchronized void assign(UUID runId) {
      this.runId = runId;
    }

    private synchronized boolean matches(UUID runId) {
      return this.runId != null && this.runId.equals(runId);
    }

    private synchronized void attach(PipelineExecutionHandle handle) {
      this.handle = handle;
      if (cancelRequested && !complete) handle.cancel();
    }

    private synchronized void cancel() {
      cancelRequested = true;
      if (handle != null && !complete) handle.cancel();
    }

    private synchronized void complete() {
      complete = true;
    }
  }
}
