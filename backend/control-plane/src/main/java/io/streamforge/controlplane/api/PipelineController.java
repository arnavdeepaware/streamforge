package io.streamforge.controlplane.api;

import io.streamforge.controlplane.service.PipelineApiService;
import io.streamforge.controlplane.service.PipelineRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Version-one REST endpoints for pipeline configuration; these endpoints never start pipelines. */
@RestController
@RequestMapping("/api/v1/pipelines")
@Tag(name = "Pipelines", description = "Versioned pipeline definitions and immutable revisions")
public final class PipelineController {
  private final PipelineApiService pipelines;
  private final PipelineRunService runs;

  public PipelineController(PipelineApiService pipelines, PipelineRunService runs) {
    this.pipelines = pipelines;
    this.runs = runs;
  }

  @PostMapping
  @Operation(summary = "Create a pipeline definition with revision one")
  public ResponseEntity<PipelineDefinitionResponse> create(
      @RequestBody CreatePipelineRequest request) {
    PipelineDefinitionResponse created = pipelines.create(request);
    return ResponseEntity.created(URI.create("/api/v1/pipelines/" + created.id())).body(created);
  }

  @GetMapping
  @Operation(summary = "List pipeline definitions with pagination")
  public PageResponse<PipelineSummaryResponse> list(
      @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
    return pipelines.list(pageable);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a pipeline definition and its latest revision")
  public PipelineDefinitionResponse get(@PathVariable UUID id) {
    return pipelines.get(id);
  }

  @PostMapping("/validate")
  @Operation(summary = "Validate pipeline configuration without saving it")
  public ValidationResult validate(@RequestBody CreatePipelineRevisionRequest request) {
    return pipelines.validate(request);
  }

  @PostMapping("/{id}/revisions")
  @Operation(summary = "Create an immutable pipeline configuration revision")
  public ResponseEntity<PipelineDefinitionResponse> createRevision(
      @PathVariable UUID id, @RequestBody CreatePipelineRevisionRequest request) {
    PipelineDefinitionResponse updated = pipelines.createRevision(id, request);
    return ResponseEntity.created(URI.create("/api/v1/pipelines/" + id)).body(updated);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update mutable pipeline metadata")
  public PipelineDefinitionResponse updateMetadata(
      @PathVariable UUID id, @RequestBody UpdatePipelineMetadataRequest request) {
    return pipelines.updateMetadata(id, request);
  }

  @PostMapping("/{id}/archive")
  @Operation(summary = "Archive a pipeline while preserving all revisions")
  public PipelineDefinitionResponse archive(@PathVariable UUID id) {
    return pipelines.archive(id);
  }

  @PostMapping("/{id}/runs")
  @Operation(summary = "Start the latest immutable pipeline revision locally")
  public ResponseEntity<PipelineRunResponse> start(
      @PathVariable UUID id, @RequestBody(required = false) StartPipelineRequest request) {
    PipelineRunResponse run = runs.start(id, request);
    return ResponseEntity.accepted().body(run);
  }

  @PostMapping("/{id}/runs/{runId}/stop")
  @Operation(summary = "Request graceful cancellation of a running pipeline")
  public PipelineRunResponse stop(@PathVariable UUID id, @PathVariable UUID runId) {
    return runs.stop(id, runId);
  }

  @GetMapping("/{id}/runs/{runId}")
  @Operation(summary = "Get current pipeline run status")
  public PipelineRunResponse status(@PathVariable UUID id, @PathVariable UUID runId) {
    return runs.status(id, runId);
  }

  @GetMapping("/{id}/runs/{runId}/report")
  @Operation(summary = "Get the final pipeline run report")
  public PipelineReportResponse report(@PathVariable UUID id, @PathVariable UUID runId) {
    return runs.report(id, runId);
  }

  @GetMapping(path = "/{id}/runs/{runId}/events", produces = "text/event-stream")
  @Operation(summary = "Stream current pipeline status and final report availability over SSE")
  public SseEmitter events(@PathVariable UUID id, @PathVariable UUID runId) {
    SseEmitter emitter = new SseEmitter(0L);
    try {
      PipelineRunResponse status = runs.status(id, runId);
      emitter.send(SseEmitter.event().name("pipeline-status").data(status));
      if (!status.state().active()) emitter.complete();
    } catch (Exception exception) {
      emitter.completeWithError(exception);
    }
    return emitter;
  }
}
