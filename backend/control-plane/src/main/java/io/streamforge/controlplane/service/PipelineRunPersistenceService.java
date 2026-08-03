package io.streamforge.controlplane.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.streamforge.controlplane.api.DeadLetterOptionsRequest;
import io.streamforge.controlplane.api.FieldViolation;
import io.streamforge.controlplane.api.PipelineReportResponse;
import io.streamforge.controlplane.api.PipelineRunResponse;
import io.streamforge.controlplane.execution.LocalDeadLetterOptions;
import io.streamforge.controlplane.execution.PipelineExecutionCommand;
import io.streamforge.controlplane.execution.PipelineExecutionResult;
import io.streamforge.controlplane.execution.PipelineRunState;
import io.streamforge.controlplane.persistence.entity.InputDefinitionEntity;
import io.streamforge.controlplane.persistence.entity.OutputDefinitionEntity;
import io.streamforge.controlplane.persistence.entity.PipelineDefinitionEntity;
import io.streamforge.controlplane.persistence.entity.PipelineRevisionEntity;
import io.streamforge.controlplane.persistence.entity.PipelineRunEntity;
import io.streamforge.controlplane.persistence.entity.TransformDefinitionEntity;
import io.streamforge.controlplane.persistence.repository.InputDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.OutputDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.PipelineDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.PipelineRevisionRepository;
import io.streamforge.controlplane.persistence.repository.PipelineRunRepository;
import io.streamforge.controlplane.persistence.repository.TransformDefinitionRepository;
import io.streamforge.pipelineruntime.PipelineCounters;
import io.streamforge.pipelineruntime.PipelineFailure;
import io.streamforge.pipelineruntime.PipelineOutcome;
import io.streamforge.pipelineruntime.PipelineReport;
import io.streamforge.pipelineruntime.PipelineStage;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns transactional pipeline-run persistence independently from asynchronous execution. */
@Service
public class PipelineRunPersistenceService {
  private static final String RESTART_FAILURE =
      "local execution was interrupted by a control-plane restart";

  private final PipelineRunRepository runs;
  private final PipelineDefinitionRepository pipelines;
  private final PipelineRevisionRepository revisions;
  private final InputDefinitionRepository inputs;
  private final TransformDefinitionRepository transforms;
  private final OutputDefinitionRepository outputs;
  private final ObjectMapper mapper;

  public PipelineRunPersistenceService(
      PipelineRunRepository runs,
      PipelineDefinitionRepository pipelines,
      PipelineRevisionRepository revisions,
      InputDefinitionRepository inputs,
      TransformDefinitionRepository transforms,
      OutputDefinitionRepository outputs,
      ObjectMapper mapper) {
    this.runs = runs;
    this.pipelines = pipelines;
    this.revisions = revisions;
    this.inputs = inputs;
    this.transforms = transforms;
    this.outputs = outputs;
    this.mapper = mapper;
  }

  /** Creates and commits a STARTING run plus its immutable execution command. */
  @Transactional
  public PreparedRun createStarting(UUID pipelineId, DeadLetterOptionsRequest request) {
    PipelineDefinitionEntity pipeline =
        pipelines
            .findById(pipelineId)
            .orElseThrow(() -> new NoSuchElementException("pipeline definition was not found"));
    if (pipeline.archivedAt() != null) {
      throw new IllegalStateException("archived pipelines cannot be started");
    }
    PipelineRevisionEntity revision =
        revisions
            .findFirstByPipelineDefinitionIdOrderByRevisionNumberDesc(pipelineId)
            .orElseThrow(() -> new NoSuchElementException("pipeline definition was not found"));
    Optional<LocalDeadLetterOptions> options = deadLetterOptions(request);
    PipelineRunEntity run =
        runs.save(
            new PipelineRunEntity(
                pipelineId, revision.id(), request == null ? null : json(request)));
    run.transition(PipelineRunState.VALIDATED, null);
    run.transition(PipelineRunState.STARTING, null);
    runs.save(run);
    return new PreparedRun(response(run), command(run, revision, options));
  }

  @Transactional
  public PipelineRunResponse running(UUID runId) {
    PipelineRunEntity run = required(runId);
    if (run.state().canTransitionTo(PipelineRunState.RUNNING)) {
      run.transition(PipelineRunState.RUNNING, null);
    }
    return response(runs.save(run));
  }

  @Transactional
  public PipelineRunResponse requestStop(UUID pipelineId, UUID runId) {
    PipelineRunEntity run = required(pipelineId, runId);
    if (run.state() != PipelineRunState.RUNNING && run.state() != PipelineRunState.STARTING) {
      throw new IllegalStateException("pipeline run is not running");
    }
    run.transition(PipelineRunState.STOPPING, null);
    return response(runs.save(run));
  }

  @Transactional
  public PipelineRunResponse finish(UUID runId, PipelineExecutionResult result) {
    PipelineRunEntity run = required(runId);
    String report = json(result.report());
    String deadLetter = result.deadLetterArtifactPath().orElse(null);
    if (run.state() == PipelineRunState.STOPPING
        || result.report().outcome() == PipelineOutcome.CANCELLED) {
      if (run.state() != PipelineRunState.STOPPING) {
        run.transition(PipelineRunState.STOPPING, null);
      }
      run.stop(report, deadLetter);
    } else if (result.report().outcome() == PipelineOutcome.FAILED) {
      run.fail(report, failureSummary(result), deadLetter);
    } else {
      run.complete(report, result.outputArtifactPath().orElse(null), deadLetter);
    }
    return response(runs.save(run));
  }

  @Transactional
  public PipelineRunResponse fail(UUID runId, Throwable failure) {
    PipelineRunEntity run = required(runId);
    if (run.state().active()) {
      String summary = safe(failure);
      run.fail(json(failedReport(summary)), summary, run.deadLetterArtifactPath());
    }
    return response(runs.save(run));
  }

  /** Marks local work that cannot survive process restart as failed. */
  @Transactional
  public List<PipelineRunResponse> reconcileInterrupted() {
    return runs
        .findByStateIn(
            EnumSet.of(
                PipelineRunState.CREATED,
                PipelineRunState.VALIDATED,
                PipelineRunState.STARTING,
                PipelineRunState.RUNNING,
                PipelineRunState.STOPPING))
        .stream()
        .map(
            run -> {
              String report =
                  run.finalReport() == null
                      ? json(failedReport(RESTART_FAILURE))
                      : run.finalReport();
              run.fail(report, RESTART_FAILURE, run.deadLetterArtifactPath());
              return response(runs.save(run));
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public StoredRun get(UUID pipelineId, UUID runId) {
    return stored(required(pipelineId, runId));
  }

  @Transactional(readOnly = true)
  public Optional<StoredRun> latest(UUID pipelineId) {
    if (!pipelines.existsById(pipelineId)) {
      throw new NoSuchElementException("pipeline definition was not found");
    }
    return runs.findTopByPipelineDefinitionIdOrderByCreatedAtDesc(pipelineId).map(this::stored);
  }

  @Transactional(readOnly = true)
  public PipelineReportResponse report(UUID pipelineId, UUID runId) {
    PipelineRunEntity run = required(pipelineId, runId);
    return new PipelineReportResponse(
        run.id(), run.state(), run.finalReport() == null ? null : tree(run.finalReport()));
  }

  private PipelineExecutionCommand command(
      PipelineRunEntity run,
      PipelineRevisionEntity revision,
      Optional<LocalDeadLetterOptions> deadLetter) {
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

  private Optional<LocalDeadLetterOptions> deadLetterOptions(DeadLetterOptionsRequest request) {
    if (request == null) return Optional.empty();
    try {
      return Optional.of(
          new LocalDeadLetterOptions(
              request.policy(), request.includePayload(), request.maximumPayloadBytes()));
    } catch (IllegalArgumentException exception) {
      throw new ApiValidationException(
          List.of(new FieldViolation("deadLetter", exception.getMessage())));
    }
  }

  private PipelineRunEntity required(UUID pipelineId, UUID runId) {
    PipelineRunEntity run = required(runId);
    if (!run.pipelineDefinitionId().equals(pipelineId)) {
      throw new NoSuchElementException("pipeline run was not found");
    }
    return run;
  }

  private PipelineRunEntity required(UUID runId) {
    return runs.findById(runId)
        .orElseThrow(() -> new NoSuchElementException("pipeline run was not found"));
  }

  private StoredRun stored(PipelineRunEntity run) {
    return new StoredRun(
        response(run),
        run.finalReport() == null ? null : tree(run.finalReport()),
        run.outputArtifactPath(),
        run.deadLetterArtifactPath());
  }

  private static PipelineRunResponse response(PipelineRunEntity run) {
    return new PipelineRunResponse(
        run.id(),
        run.pipelineDefinitionId(),
        run.pipelineRevisionId(),
        run.state(),
        run.failureSummary(),
        run.startedAt(),
        run.finishedAt());
  }

  private static String failureSummary(PipelineExecutionResult result) {
    return result.report().failures().isEmpty()
        ? "pipeline execution failed"
        : result.report().failures().getFirst().detail();
  }

  private static String safe(Throwable failure) {
    String message = failure.getMessage();
    String value =
        message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    return value.substring(0, Math.min(value.length(), 512));
  }

  private static PipelineReport failedReport(String detail) {
    return new PipelineReport(
        new PipelineCounters(0, 0, 0, 0, 0, 1),
        List.of(new PipelineFailure(PipelineStage.CONFIGURATION, "pipeline", detail)),
        0,
        PipelineOutcome.FAILED);
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

  public record PreparedRun(PipelineRunResponse response, PipelineExecutionCommand command) {}

  public record StoredRun(
      PipelineRunResponse response,
      JsonNode finalReport,
      String outputArtifactPath,
      String deadLetterArtifactPath) {}
}
