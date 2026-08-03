package io.streamforge.controlplane.api;

import io.streamforge.controlplane.service.PipelineApiService;
import io.streamforge.controlplane.service.PipelinePreviewService;
import io.streamforge.controlplane.service.PipelineRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Version-one REST endpoints for pipeline configuration and finite local-run monitoring. */
@RestController
@RequestMapping("/api/v1/pipelines")
@Tag(name = "Pipelines", description = "Versioned pipeline definitions and immutable revisions")
public final class PipelineController {
  private final PipelineApiService pipelines;
  private final PipelineRunService runs;
  private final PipelinePreviewService previews;

  public PipelineController(
      PipelineApiService pipelines, PipelineRunService runs, PipelinePreviewService previews) {
    this.pipelines = pipelines;
    this.runs = runs;
    this.previews = previews;
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
  public PipelineDefinitionResponse get(@PathVariable("id") UUID id) {
    return pipelines.get(id);
  }

  @PostMapping("/validate")
  @Operation(summary = "Validate pipeline configuration without saving it")
  public ValidationResult validate(@RequestBody CreatePipelineRevisionRequest request) {
    return pipelines.validate(request);
  }

  @GetMapping("/preview/canonical-fields")
  @Operation(summary = "List canonical fields available for safe mapping")
  public List<CanonicalFieldResponse> canonicalFields() {
    return previews.canonicalFields();
  }

  @PostMapping("/preview")
  @Operation(
      summary = "Execute a declarative transform and output blueprint against one sample event")
  public PipelinePreviewResponse preview(@RequestBody PipelinePreviewRequest request) {
    return previews.preview(request);
  }

  @PostMapping("/{id}/revisions")
  @Operation(summary = "Create an immutable pipeline configuration revision")
  public ResponseEntity<PipelineDefinitionResponse> createRevision(
      @PathVariable("id") UUID id, @RequestBody CreatePipelineRevisionRequest request) {
    PipelineDefinitionResponse updated = pipelines.createRevision(id, request);
    return ResponseEntity.created(URI.create("/api/v1/pipelines/" + id)).body(updated);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update mutable pipeline metadata")
  public PipelineDefinitionResponse updateMetadata(
      @PathVariable("id") UUID id, @RequestBody UpdatePipelineMetadataRequest request) {
    return pipelines.updateMetadata(id, request);
  }

  @PostMapping("/{id}/archive")
  @Operation(summary = "Archive a pipeline while preserving all revisions")
  public PipelineDefinitionResponse archive(@PathVariable("id") UUID id) {
    return pipelines.archive(id);
  }

  @PostMapping("/{id}/runs")
  @Operation(summary = "Start the latest immutable pipeline revision locally")
  public ResponseEntity<PipelineRunResponse> start(
      @PathVariable("id") UUID id, @RequestBody(required = false) StartPipelineRequest request) {
    PipelineRunResponse run = runs.start(id, request);
    return ResponseEntity.accepted().body(run);
  }

  @PostMapping("/{id}/runs/{runId}/stop")
  @Operation(summary = "Request graceful cancellation of a running pipeline")
  public PipelineRunResponse stop(@PathVariable("id") UUID id, @PathVariable("runId") UUID runId) {
    return runs.stop(id, runId);
  }

  @GetMapping("/{id}/runs/{runId}")
  @Operation(summary = "Get current pipeline run status")
  public PipelineRunResponse status(
      @PathVariable("id") UUID id, @PathVariable("runId") UUID runId) {
    return runs.status(id, runId);
  }

  @GetMapping("/{id}/runs/{runId}/report")
  @Operation(summary = "Get the final pipeline run report")
  public PipelineReportResponse report(
      @PathVariable("id") UUID id, @PathVariable("runId") UUID runId) {
    return runs.report(id, runId);
  }

  @GetMapping("/{id}/runs/{runId}/monitoring")
  @Operation(summary = "Get a bounded live monitoring snapshot for one pipeline run")
  public PipelineMonitoringResponse monitoring(
      @PathVariable("id") UUID id, @PathVariable("runId") UUID runId) {
    return runs.monitoring(id, runId);
  }

  @GetMapping("/{id}/runs/{runId}/dead-letters")
  @Operation(summary = "List recent safe dead-letter records retained for one local run")
  public List<DeadLetterResponse> deadLetters(
      @PathVariable("id") UUID id, @PathVariable("runId") UUID runId) {
    return runs.monitoring(id, runId).deadLetters();
  }

  @GetMapping("/{id}/runs/{runId}/dead-letters/{failureId}")
  @Operation(summary = "Get one recent safe dead-letter record")
  public DeadLetterResponse deadLetter(
      @PathVariable("id") UUID id,
      @PathVariable("runId") UUID runId,
      @PathVariable("failureId") String failureId) {
    return runs.monitoring(id, runId).deadLetters().stream()
        .filter(record -> record.failureId().equals(failureId))
        .findFirst()
        .orElseThrow(
            () -> new java.util.NoSuchElementException("dead-letter record was not found"));
  }

  @GetMapping("/{id}/runs/{runId}/output")
  @Operation(summary = "Download completed finite JSONL or CSV output")
  public ResponseEntity<Resource> output(
      @PathVariable("id") UUID id, @PathVariable("runId") UUID runId) {
    Resource output = runs.output(id, runId);
    String filename = output.getFilename() == null ? "pipeline-output" : output.getFilename();
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString())
        .body(output);
  }

  @GetMapping(path = "/{id}/runs/{runId}/events", produces = "text/event-stream")
  @Operation(summary = "Stream current pipeline status and final report availability over SSE")
  public SseEmitter events(@PathVariable("id") UUID id, @PathVariable("runId") UUID runId) {
    return runs.events(id, runId);
  }
}
