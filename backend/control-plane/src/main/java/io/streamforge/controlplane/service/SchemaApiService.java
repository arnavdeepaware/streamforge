package io.streamforge.controlplane.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.streamforge.controlplane.api.CreateSchemaRequest;
import io.streamforge.controlplane.api.CreateSchemaRevisionRequest;
import io.streamforge.controlplane.api.FieldViolation;
import io.streamforge.controlplane.api.PageResponse;
import io.streamforge.controlplane.api.SchemaDefinitionResponse;
import io.streamforge.controlplane.api.SchemaRevisionResponse;
import io.streamforge.controlplane.api.SchemaSummaryResponse;
import io.streamforge.controlplane.api.UpdateSchemaMetadataRequest;
import io.streamforge.controlplane.api.ValidationResult;
import io.streamforge.controlplane.persistence.entity.SchemaDefinitionEntity;
import io.streamforge.controlplane.persistence.entity.SchemaRevisionEntity;
import io.streamforge.controlplane.persistence.repository.SchemaDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.SchemaRevisionRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional version-one API service for JSON Schema identities and immutable revisions. */
@Service
public class SchemaApiService {
  private final SchemaDocumentValidator documentValidator;
  private final SchemaDefinitionRepository schemas;
  private final SchemaRevisionRepository revisions;
  private final ObjectMapper objectMapper;

  public SchemaApiService(
      SchemaDocumentValidator documentValidator,
      SchemaDefinitionRepository schemas,
      SchemaRevisionRepository revisions,
      ObjectMapper objectMapper) {
    this.documentValidator = documentValidator;
    this.schemas = schemas;
    this.revisions = revisions;
    this.objectMapper = objectMapper;
  }

  /** Validates a JSON Schema document without saving it. */
  @Transactional(readOnly = true)
  public ValidationResult validate(CreateSchemaRevisionRequest request) {
    documentValidator.validate(request == null ? null : request.document());
    return new ValidationResult(true, List.of());
  }

  /** Creates a schema catalog identity and revision one atomically. */
  @Transactional
  public SchemaDefinitionResponse create(CreateSchemaRequest request) {
    validateCreate(request);
    String document = documentValidator.validate(request.document());
    SchemaDefinitionEntity schema =
        schemas.save(
            new SchemaDefinitionEntity(request.name().trim(), description(request.description())));
    SchemaRevisionEntity revision =
        revisions.save(new SchemaRevisionEntity(schema.id(), 1, document));
    return toDefinition(schema, revision);
  }

  /** Returns a stable paged view of schema definitions. */
  @Transactional(readOnly = true)
  public PageResponse<SchemaSummaryResponse> list(Pageable pageable) {
    Page<SchemaDefinitionEntity> page = schemas.findAll(pageable);
    List<SchemaSummaryResponse> items =
        page.getContent().stream()
            .map(
                schema ->
                    new SchemaSummaryResponse(
                        schema.id(),
                        schema.name(),
                        schema.description(),
                        schema.archivedAt() != null,
                        latestRevision(schema.id()).revisionNumber(),
                        schema.createdAt(),
                        schema.updatedAt()))
            .toList();
    return new PageResponse<>(
        items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  /** Returns schema metadata and the latest immutable document revision. */
  @Transactional(readOnly = true)
  public SchemaDefinitionResponse get(UUID id) {
    SchemaDefinitionEntity schema = schema(id);
    return toDefinition(schema, latestRevision(id));
  }

  /** Persists a new immutable document revision. */
  @Transactional
  public SchemaDefinitionResponse createRevision(UUID id, CreateSchemaRevisionRequest request) {
    SchemaDefinitionEntity schema = schema(id);
    String document = documentValidator.validate(request == null ? null : request.document());
    long number = latestRevision(id).revisionNumber();
    if (number == Long.MAX_VALUE) {
      throw new IllegalStateException("schema revision number limit reached");
    }
    SchemaRevisionEntity revision =
        revisions.save(new SchemaRevisionEntity(id, number + 1, document));
    return toDefinition(schema, revision);
  }

  /** Updates only mutable catalog metadata. */
  @Transactional
  public SchemaDefinitionResponse updateMetadata(UUID id, UpdateSchemaMetadataRequest request) {
    if (request == null) {
      throw validation("request", "is required");
    }
    validateMetadata(request.name(), request.description());
    SchemaDefinitionEntity schema = schema(id);
    schema.updateMetadata(request.name().trim(), description(request.description()));
    return toDefinition(schema, latestRevision(id));
  }

  /** Archives a schema identity without deleting its historical document revisions. */
  @Transactional
  public SchemaDefinitionResponse archive(UUID id) {
    SchemaDefinitionEntity schema = schema(id);
    schema.archive();
    return toDefinition(schema, latestRevision(id));
  }

  private SchemaDefinitionResponse toDefinition(
      SchemaDefinitionEntity schema, SchemaRevisionEntity revision) {
    return new SchemaDefinitionResponse(
        schema.id(),
        schema.name(),
        schema.description(),
        schema.archivedAt() != null,
        schema.version(),
        schema.createdAt(),
        schema.updatedAt(),
        new SchemaRevisionResponse(
            revision.id(),
            revision.revisionNumber(),
            json(revision.document()),
            revision.createdAt()));
  }

  private SchemaDefinitionEntity schema(UUID id) {
    if (id == null) {
      throw validation("id", "is required");
    }
    return schemas
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("schema was not found"));
  }

  private SchemaRevisionEntity latestRevision(UUID id) {
    return revisions
        .findFirstBySchemaDefinitionIdOrderByRevisionNumberDesc(id)
        .orElseThrow(() -> new IllegalStateException("schema has no revision"));
  }

  private JsonNode json(String value) {
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("persisted schema document is not valid JSON", exception);
    }
  }

  private static void validateCreate(CreateSchemaRequest request) {
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

  private static String description(String description) {
    return description == null ? "" : description;
  }

  private static ApiValidationException validation(String field, String message) {
    return new ApiValidationException(List.of(new FieldViolation(field, message)));
  }
}
