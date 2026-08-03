package io.streamforge.controlplane.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.streamforge.controlplane.api.CreatePipelineRequest;
import io.streamforge.controlplane.api.CreatePipelineRevisionRequest;
import io.streamforge.controlplane.api.FieldViolation;
import io.streamforge.controlplane.api.PageResponse;
import io.streamforge.controlplane.api.PipelineConfigurationResponse;
import io.streamforge.controlplane.api.PipelineDefinitionResponse;
import io.streamforge.controlplane.api.PipelineRevisionResponse;
import io.streamforge.controlplane.api.PipelineSummaryResponse;
import io.streamforge.controlplane.api.UpdatePipelineMetadataRequest;
import io.streamforge.controlplane.api.ValidationResult;
import io.streamforge.controlplane.persistence.entity.InputDefinitionEntity;
import io.streamforge.controlplane.persistence.entity.OutputDefinitionEntity;
import io.streamforge.controlplane.persistence.entity.PipelineDefinitionEntity;
import io.streamforge.controlplane.persistence.entity.PipelineRevisionEntity;
import io.streamforge.controlplane.persistence.entity.TransformDefinitionEntity;
import io.streamforge.controlplane.persistence.repository.InputDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.OutputDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.PipelineDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.PipelineRevisionRepository;
import io.streamforge.controlplane.persistence.repository.TransformDefinitionRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional version-one API service for pipeline identities and immutable configuration
 * revisions.
 */
@Service
public final class PipelineApiService {
  private final PipelineConfigurationValidator configurationValidator;
  private final PipelineDefinitionRepository pipelines;
  private final InputDefinitionRepository inputs;
  private final TransformDefinitionRepository transforms;
  private final OutputDefinitionRepository outputs;
  private final PipelineRevisionRepository revisions;
  private final ObjectMapper objectMapper;

  public PipelineApiService(
      PipelineConfigurationValidator configurationValidator,
      PipelineDefinitionRepository pipelines,
      InputDefinitionRepository inputs,
      TransformDefinitionRepository transforms,
      OutputDefinitionRepository outputs,
      PipelineRevisionRepository revisions,
      ObjectMapper objectMapper) {
    this.configurationValidator = configurationValidator;
    this.pipelines = pipelines;
    this.inputs = inputs;
    this.transforms = transforms;
    this.outputs = outputs;
    this.revisions = revisions;
    this.objectMapper = objectMapper;
  }

  /** Validates a pipeline configuration without creating definitions or revisions. */
  @Transactional(readOnly = true)
  public ValidationResult validate(CreatePipelineRevisionRequest request) {
    configurationValidator.validate(request == null ? null : request.configuration());
    return new ValidationResult(true, List.of());
  }

  /** Creates a durable pipeline identity and revision one atomically. */
  @Transactional
  public PipelineDefinitionResponse create(CreatePipelineRequest request) {
    validateCreateRequest(request);
    PipelineConfigurationValidator.ValidatedPipelineConfiguration configuration =
        configurationValidator.validate(request.configuration());
    PipelineDefinitionEntity pipeline =
        pipelines.save(
            new PipelineDefinitionEntity(
                request.name().trim(), normalizedDescription(request.description())));
    PipelineRevisionEntity revision = persistRevision(pipeline.id(), 1, configuration);
    return toDefinition(pipeline, revision);
  }

  /** Returns a stable paged view of pipeline identities and their latest revision numbers. */
  @Transactional(readOnly = true)
  public PageResponse<PipelineSummaryResponse> list(Pageable pageable) {
    Page<PipelineDefinitionEntity> page = pipelines.findAll(pageable);
    List<PipelineSummaryResponse> items =
        page.getContent().stream()
            .map(
                pipeline ->
                    new PipelineSummaryResponse(
                        pipeline.id(),
                        pipeline.name(),
                        pipeline.description(),
                        pipeline.archivedAt() != null,
                        latestRevision(pipeline.id()).revisionNumber(),
                        pipeline.createdAt(),
                        pipeline.updatedAt()))
            .toList();
    return new PageResponse<>(
        items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  /** Returns a pipeline identity and its latest immutable revision. */
  @Transactional(readOnly = true)
  public PipelineDefinitionResponse get(UUID id) {
    PipelineDefinitionEntity pipeline = pipeline(id);
    return toDefinition(pipeline, latestRevision(id));
  }

  /** Persists a new immutable configuration revision without modifying prior revisions. */
  @Transactional
  public PipelineDefinitionResponse createRevision(UUID id, CreatePipelineRevisionRequest request) {
    PipelineDefinitionEntity pipeline = pipeline(id);
    PipelineConfigurationValidator.ValidatedPipelineConfiguration configuration =
        configurationValidator.validate(request == null ? null : request.configuration());
    long currentRevision = latestRevision(id).revisionNumber();
    if (currentRevision == Long.MAX_VALUE) {
      throw new IllegalStateException("pipeline revision number limit reached");
    }
    PipelineRevisionEntity revision = persistRevision(id, currentRevision + 1, configuration);
    return toDefinition(pipeline, revision);
  }

  /** Updates only catalog metadata; immutable revision configuration remains untouched. */
  @Transactional
  public PipelineDefinitionResponse updateMetadata(UUID id, UpdatePipelineMetadataRequest request) {
    if (request == null) {
      throw validation("request", "is required");
    }
    validateMetadata(request.name(), request.description());
    PipelineDefinitionEntity pipeline = pipeline(id);
    pipeline.updateMetadata(request.name().trim(), normalizedDescription(request.description()));
    return toDefinition(pipeline, latestRevision(id));
  }

  /** Marks a pipeline as archived while retaining all historical revisions. */
  @Transactional
  public PipelineDefinitionResponse archive(UUID id) {
    PipelineDefinitionEntity pipeline = pipeline(id);
    pipeline.archive();
    return toDefinition(pipeline, latestRevision(id));
  }

  private PipelineRevisionEntity persistRevision(
      UUID pipelineId,
      long revisionNumber,
      PipelineConfigurationValidator.ValidatedPipelineConfiguration configuration) {
    InputDefinitionEntity input =
        inputs.save(new InputDefinitionEntity(pipelineId, configuration.input()));
    TransformDefinitionEntity transform =
        transforms.save(new TransformDefinitionEntity(pipelineId, configuration.transform()));
    OutputDefinitionEntity output =
        outputs.save(new OutputDefinitionEntity(pipelineId, configuration.output()));
    return revisions.save(
        new PipelineRevisionEntity(
            pipelineId,
            revisionNumber,
            input.id(),
            transform.id(),
            output.id(),
            configuration.blueprint()));
  }

  private PipelineDefinitionResponse toDefinition(
      PipelineDefinitionEntity pipeline, PipelineRevisionEntity revision) {
    return new PipelineDefinitionResponse(
        pipeline.id(),
        pipeline.name(),
        pipeline.description(),
        pipeline.archivedAt() != null,
        pipeline.version(),
        pipeline.createdAt(),
        pipeline.updatedAt(),
        toRevision(revision));
  }

  private PipelineRevisionResponse toRevision(PipelineRevisionEntity revision) {
    InputDefinitionEntity input = inputs.findById(revision.inputDefinitionId()).orElseThrow();
    TransformDefinitionEntity transform =
        transforms.findById(revision.transformDefinitionId()).orElseThrow();
    OutputDefinitionEntity output = outputs.findById(revision.outputDefinitionId()).orElseThrow();
    return new PipelineRevisionResponse(
        revision.id(),
        revision.revisionNumber(),
        new PipelineConfigurationResponse(
            json(input.configuration()),
            json(transform.configuration()),
            revision.blueprintConfiguration() == null
                ? NullNode.instance
                : json(revision.blueprintConfiguration()),
            json(output.configuration())),
        revision.createdAt());
  }

  private PipelineRevisionEntity latestRevision(UUID pipelineId) {
    return revisions
        .findFirstByPipelineDefinitionIdOrderByRevisionNumberDesc(pipelineId)
        .orElseThrow(() -> new IllegalStateException("pipeline has no revision"));
  }

  private PipelineDefinitionEntity pipeline(UUID id) {
    if (id == null) {
      throw validation("id", "is required");
    }
    return pipelines
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("pipeline definition was not found"));
  }

  private JsonNode json(String value) {
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("persisted configuration is not valid JSON", exception);
    }
  }

  private static void validateCreateRequest(CreatePipelineRequest request) {
    if (request == null) {
      throw validation("request", "is required");
    }
    validateMetadata(request.name(), request.description());
  }

  private static void validateMetadata(String name, String description) {
    if (name == null || name.isBlank()) {
      throw validation("name", "is required");
    }
    if (name.trim().length() > 160) {
      throw validation("name", "must not exceed 160 characters");
    }
    if (description != null && description.length() > 500) {
      throw validation("description", "must not exceed 500 characters");
    }
  }

  private static String normalizedDescription(String description) {
    return description == null ? "" : description;
  }

  private static ApiValidationException validation(String field, String message) {
    return new ApiValidationException(List.of(new FieldViolation(field, message)));
  }
}
