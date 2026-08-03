package io.streamforge.controlplane.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable JSON Schema document associated with a monotonically increasing revision number. */
@Entity
@Table(name = "schema_revisions")
public class SchemaRevisionEntity extends AuditedEntity {
  @Column(name = "schema_definition_id", nullable = false)
  private UUID schemaDefinitionId;

  @Column(name = "revision_number", nullable = false)
  private long revisionNumber;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String document;

  protected SchemaRevisionEntity() {}

  public SchemaRevisionEntity(UUID schemaDefinitionId, long revisionNumber, String document) {
    this.schemaDefinitionId = schemaDefinitionId;
    this.revisionNumber = revisionNumber;
    this.document = document;
  }

  public UUID schemaDefinitionId() {
    return schemaDefinitionId;
  }

  public long revisionNumber() {
    return revisionNumber;
  }

  public String document() {
    return document;
  }
}
