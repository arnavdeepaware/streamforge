package io.streamforge.controlplane.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.streamforge.controlplane.api.PipelineMonitoringResponse;
import io.streamforge.controlplane.api.PipelineReportResponse;
import io.streamforge.controlplane.api.PipelineRunResponse;
import io.streamforge.controlplane.api.StartPipelineRequest;
import io.streamforge.controlplane.execution.PipelineExecutionBackend;
import io.streamforge.controlplane.execution.PipelineExecutionCommand;
import io.streamforge.controlplane.execution.PipelineExecutionHandle;
import io.streamforge.controlplane.execution.PipelineExecutionListener;
import io.streamforge.controlplane.execution.PipelineRunState;
import io.streamforge.controlplane.persistence.entity.InputDefinitionEntity;
import io.streamforge.controlplane.persistence.entity.OutputDefinitionEntity;
import io.streamforge.controlplane.persistence.entity.PipelineRevisionEntity;
import io.streamforge.controlplane.persistence.entity.PipelineRunEntity;
import io.streamforge.controlplane.persistence.entity.TransformDefinitionEntity;
import io.streamforge.controlplane.persistence.repository.InputDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.OutputDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.PipelineRevisionRepository;
import io.streamforge.controlplane.persistence.repository.PipelineRunRepository;
import io.streamforge.controlplane.persistence.repository.TransformDefinitionRepository;
import io.streamforge.pipelineruntime.PipelineReport;
import io.streamforge.pipelineruntime.PipelineRunMetrics;
import io.streamforge.pipelineruntime.deadletter.DeadLetterRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Coordinates durable lifecycle state with one pluggable execution backend. */
@Service
public class PipelineRunService {
  private final PipelineRunRepository runs;
  private final PipelineRevisionRepository revisions;
  private final InputDefinitionRepository inputs;
  private final TransformDefinitionRepository transforms;
  private final OutputDefinitionRepository outputs;
  private final PipelineExecutionBackend backend;
  private final ObjectMapper mapper;
  private final PipelineRunMonitor monitor;
  private final ConcurrentHashMap<UUID, PipelineExecutionHandle> active = new ConcurrentHashMap<>();
  private final Counter started;
  private final Counter completed;
  private final Counter failed;

  public PipelineRunService(
      PipelineRunRepository runs,
      PipelineRevisionRepository revisions,
      InputDefinitionRepository inputs,
      TransformDefinitionRepository transforms,
      OutputDefinitionRepository outputs,
      PipelineExecutionBackend backend,
      ObjectMapper mapper,
      MeterRegistry metrics,
      PipelineRunMonitor monitor) {
    this.runs = runs;
    this.revisions = revisions;
    this.inputs = inputs;
    this.transforms = transforms;
    this.outputs = outputs;
    this.backend = backend;
    this.mapper = mapper;
    this.monitor = monitor;
    started = metrics.counter("streamforge.pipeline.runs", "outcome", "started");
    completed = metrics.counter("streamforge.pipeline.runs", "outcome", "completed");
    failed = metrics.counter("streamforge.pipeline.runs", "outcome", "failed");
  }

  /**
   * Starts the latest pipeline revision once; concurrent starts for the same pipeline are rejected.
   */
  @Transactional
  public PipelineRunResponse start(UUID pipelineId, StartPipelineRequest request) {
    PipelineExecutionHandle marker = () -> {};
    if (active.putIfAbsent(pipelineId, marker) != null)
      throw new IllegalStateException("pipeline already has an active run");
    try {
      PipelineRevisionEntity revision =
          revisions
              .findFirstByPipelineDefinitionIdOrderByRevisionNumberDesc(pipelineId)
              .orElseThrow(() -> new NoSuchElementException("pipeline definition was not found"));
      String deadLetter =
          request == null || request.deadLetter() == null ? null : json(request.deadLetter());
      PipelineRunEntity run =
          runs.save(new PipelineRunEntity(pipelineId, revision.id(), deadLetter));
      monitor.register(run.id(), run.state());
      run.transition(PipelineRunState.VALIDATED, null);
      monitor.state(run.id(), run.state());
      run.transition(PipelineRunState.STARTING, null);
      monitor.state(run.id(), run.state());
      PipelineExecutionHandle handle =
          backend.start(
              command(run, revision, request == null ? null : request.deadLetter()),
              new Listener(run.id(), pipelineId));
      active.replace(pipelineId, marker, handle);
      started.increment();
      return response(run);
    } catch (RuntimeException exception) {
      active.remove(pipelineId, marker);
      throw exception;
    }
  }

  /** Requests cooperative cancellation and records the stopping transition. */
  @Transactional
  public PipelineRunResponse stop(UUID pipelineId, UUID runId) {
    PipelineRunEntity run = run(pipelineId, runId);
    if (run.state() != PipelineRunState.RUNNING && run.state() != PipelineRunState.STARTING)
      throw new IllegalStateException("pipeline run is not running");
    run.transition(PipelineRunState.STOPPING, null);
    monitor.state(runId, run.state());
    PipelineExecutionHandle handle = active.get(pipelineId);
    if (handle == null)
      throw new IllegalStateException("pipeline run has no active execution handle");
    handle.cancel();
    return response(run);
  }

  @Transactional(readOnly = true)
  public PipelineRunResponse status(UUID pipelineId, UUID runId) {
    return response(run(pipelineId, runId));
  }

  @Transactional(readOnly = true)
  public PipelineReportResponse report(UUID pipelineId, UUID runId) {
    PipelineRunEntity run = run(pipelineId, runId);
    return new PipelineReportResponse(
        run.id(), run.state(), run.finalReport() == null ? null : tree(run.finalReport()));
  }

  @Transactional(readOnly = true)
  public PipelineMonitoringResponse monitoring(UUID pipelineId, UUID runId) {
    PipelineRunEntity run = run(pipelineId, runId);
    PipelineMonitoringResponse snapshot = monitor.snapshot(runId);
    return snapshot.state() == run.state() ? snapshot : withState(snapshot, run.state());
  }

  @Transactional(readOnly = true)
  public SseEmitter events(UUID pipelineId, UUID runId) {
    run(pipelineId, runId);
    return monitor.subscribe(runId);
  }

  @Transactional(readOnly = true)
  public Resource output(UUID pipelineId, UUID runId) {
    PipelineRunEntity run = run(pipelineId, runId);
    if (run.state().active()) throw new IllegalStateException("pipeline output is not final");
    PipelineRevisionEntity revision = revisions.findById(run.pipelineRevisionId()).orElseThrow();
    OutputDefinitionEntity output = outputs.findById(revision.outputDefinitionId()).orElseThrow();
    JsonNode configuration = tree(output.configuration());
    JsonNode path = configuration.get("path");
    if (path == null || !path.isTextual())
      throw new IllegalStateException("output path is unavailable");
    Path file = Path.of(path.asText()).toAbsolutePath().normalize();
    if (!Files.isRegularFile(file))
      throw new NoSuchElementException("pipeline output is not available");
    return new FileSystemResource(file);
  }

  @Transactional
  void running(UUID runId) {
    transition(runId, PipelineRunState.RUNNING, null);
  }

  @Transactional
  void completed(UUID runId, UUID pipelineId, PipelineReport report) {
    PipelineRunEntity run = runs.findById(runId).orElseThrow();
    String serialized = json(report);
    if (run.state() == PipelineRunState.STOPPING || report.cancelled()) run.stop(serialized);
    else run.complete(serialized);
    runs.save(run);
    monitor.state(runId, run.state());
    completed.increment();
    active.remove(pipelineId);
  }

  @Transactional
  void failed(UUID runId, UUID pipelineId, Throwable failure) {
    PipelineRunEntity run = runs.findById(runId).orElseThrow();
    if (run.state().active()) run.transition(PipelineRunState.FAILED, safe(failure));
    runs.save(run);
    monitor.state(runId, run.state());
    failed.increment();
    active.remove(pipelineId);
  }

  private void transition(UUID runId, PipelineRunState state, String failure) {
    PipelineRunEntity run = runs.findById(runId).orElseThrow();
    if (run.state().canTransitionTo(state)) run.transition(state, failure);
    runs.save(run);
    monitor.state(runId, run.state());
  }

  private PipelineExecutionCommand command(
      PipelineRunEntity run, PipelineRevisionEntity revision, JsonNode deadLetter) {
    InputDefinitionEntity input = inputs.findById(revision.inputDefinitionId()).orElseThrow();
    TransformDefinitionEntity transform =
        transforms.findById(revision.transformDefinitionId()).orElseThrow();
    OutputDefinitionEntity output = outputs.findById(revision.outputDefinitionId()).orElseThrow();
    return new PipelineExecutionCommand(
        run.id(),
        run.pipelineDefinitionId(),
        revision.revisionNumber(),
        tree(input.configuration()),
        transform.configuration(),
        revision.blueprintConfiguration(),
        tree(output.configuration()),
        deadLetter);
  }

  private PipelineRunEntity run(UUID pipelineId, UUID runId) {
    PipelineRunEntity run =
        runs.findById(runId)
            .orElseThrow(() -> new NoSuchElementException("pipeline run was not found"));
    if (!run.pipelineDefinitionId().equals(pipelineId))
      throw new NoSuchElementException("pipeline run was not found");
    return run;
  }

  private PipelineRunResponse response(PipelineRunEntity run) {
    return new PipelineRunResponse(
        run.id(),
        run.pipelineDefinitionId(),
        run.pipelineRevisionId(),
        run.state(),
        run.failureSummary(),
        run.startedAt(),
        run.finishedAt());
  }

  private static PipelineMonitoringResponse withState(
      PipelineMonitoringResponse snapshot, PipelineRunState state) {
    return new PipelineMonitoringResponse(
        snapshot.runId(),
        state,
        snapshot.counters(),
        snapshot.eventRatePerSecond(),
        snapshot.latency(),
        snapshot.queueDepth(),
        snapshot.sequenceGapCount(),
        snapshot.duplicateCount(),
        snapshot.history(),
        snapshot.deadLetters());
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("could not serialize pipeline state", exception);
    }
  }

  private JsonNode tree(String value) {
    try {
      return mapper.readTree(value);
    } catch (Exception exception) {
      throw new IllegalStateException("stored pipeline state is invalid", exception);
    }
  }

  private static String safe(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank()
        ? failure.getClass().getSimpleName()
        : message.substring(0, Math.min(message.length(), 512));
  }

  private final class Listener implements PipelineExecutionListener {
    private final UUID runId;
    private final UUID pipelineId;

    private Listener(UUID runId, UUID pipelineId) {
      this.runId = runId;
      this.pipelineId = pipelineId;
    }

    public void onRunning() {
      running(runId);
    }

    public void onCompleted(PipelineReport report) {
      completed(runId, pipelineId, report);
    }

    public void onFailed(Throwable failure) {
      failed(runId, pipelineId, failure);
    }

    public void onMetrics(PipelineRunMetrics metrics) {
      monitor.metrics(runId, metrics);
    }

    public void onDeadLetter(DeadLetterRecord record) {
      monitor.deadLetter(runId, record);
    }
  }
}
