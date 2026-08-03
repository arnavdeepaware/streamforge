package io.streamforge.controlplane.api;

import io.streamforge.controlplane.service.SchemaApiService;
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

/** Version-one REST endpoints for schema catalog entries and immutable document revisions. */
@RestController
@RequestMapping("/api/v1/schemas")
@Tag(name = "Schemas", description = "Versioned JSON Schema catalog entries and revisions")
public final class SchemaController {
  private final SchemaApiService schemas;

  public SchemaController(SchemaApiService schemas) {
    this.schemas = schemas;
  }

  @PostMapping
  @Operation(summary = "Create a schema definition with revision one")
  public ResponseEntity<SchemaDefinitionResponse> create(@RequestBody CreateSchemaRequest request) {
    SchemaDefinitionResponse created = schemas.create(request);
    return ResponseEntity.created(URI.create("/api/v1/schemas/" + created.id())).body(created);
  }

  @GetMapping
  @Operation(summary = "List schema definitions with pagination")
  public PageResponse<SchemaSummaryResponse> list(
      @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
    return schemas.list(pageable);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a schema definition and its latest document revision")
  public SchemaDefinitionResponse get(@PathVariable UUID id) {
    return schemas.get(id);
  }

  @PostMapping("/validate")
  @Operation(summary = "Validate a JSON Schema document without saving it")
  public ValidationResult validate(@RequestBody CreateSchemaRevisionRequest request) {
    return schemas.validate(request);
  }

  @PostMapping("/{id}/revisions")
  @Operation(summary = "Create an immutable JSON Schema document revision")
  public ResponseEntity<SchemaDefinitionResponse> createRevision(
      @PathVariable UUID id, @RequestBody CreateSchemaRevisionRequest request) {
    SchemaDefinitionResponse updated = schemas.createRevision(id, request);
    return ResponseEntity.created(URI.create("/api/v1/schemas/" + id)).body(updated);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update mutable schema metadata")
  public SchemaDefinitionResponse updateMetadata(
      @PathVariable UUID id, @RequestBody UpdateSchemaMetadataRequest request) {
    return schemas.updateMetadata(id, request);
  }

  @PostMapping("/{id}/archive")
  @Operation(summary = "Archive a schema while preserving all document revisions")
  public SchemaDefinitionResponse archive(@PathVariable UUID id) {
    return schemas.archive(id);
  }
}
