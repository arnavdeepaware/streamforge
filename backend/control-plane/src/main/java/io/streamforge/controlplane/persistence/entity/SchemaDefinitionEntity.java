package io.streamforge.controlplane.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Durable schema identity with mutable catalog metadata and immutable content revisions. */
@Entity
@Table(name = "schema_definitions")
public class SchemaDefinitionEntity extends AuditedEntity {
  @Column(nullable = false, unique = true, length = 160)
  private String name;

  @Column(nullable = false, length = 500)
  private String description;

  @Column(name = "archived_at")
  private Instant archivedAt;

  protected SchemaDefinitionEntity() {}

  public SchemaDefinitionEntity(String name, String description) {
    this.name = name;
    this.description = description == null ? "" : description;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public Instant archivedAt() {
    return archivedAt;
  }

  public void updateMetadata(String name, String description) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("schema name must be non-blank");
    }
    this.name = name;
    this.description = description == null ? "" : description;
  }

  public void archive() {
    if (archivedAt == null) {
      archivedAt = Instant.now();
    }
  }
}
