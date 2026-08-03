package io.streamforge.controlplane.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Durable identity for one user-configured pipeline, independent from its revisions. */
@Entity
@Table(name = "pipeline_definitions")
public class PipelineDefinitionEntity extends AuditedEntity {
  @Column(nullable = false, unique = true, length = 160)
  private String name;

  @Column(nullable = false, length = 500)
  private String description;

  @Column(name = "archived_at")
  private Instant archivedAt;

  protected PipelineDefinitionEntity() {}

  public PipelineDefinitionEntity(String name) {
    this(name, "");
  }

  public PipelineDefinitionEntity(String name, String description) {
    this.name = name;
    this.description = description == null ? "" : description;
  }

  public String name() {
    return name;
  }

  public void rename(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("pipeline definition name must be non-blank");
    }
    this.name = name;
  }

  public String description() {
    return description;
  }

  public Instant archivedAt() {
    return archivedAt;
  }

  public void updateMetadata(String name, String description) {
    rename(name);
    this.description = description == null ? "" : description;
  }

  public void archive() {
    if (archivedAt == null) {
      archivedAt = Instant.now();
    }
  }
}
