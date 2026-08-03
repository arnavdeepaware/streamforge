package io.streamforge.controlplane.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A validated declarative transformation configuration owned by a pipeline definition. */
@Entity
@Table(name = "transform_definitions")
public class TransformDefinitionEntity extends AuditedEntity {
  @Column(name = "pipeline_definition_id", nullable = false)
  private UUID pipelineDefinitionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String configuration;

  protected TransformDefinitionEntity() {}

  public TransformDefinitionEntity(UUID pipelineDefinitionId, String configuration) {
    this.pipelineDefinitionId = pipelineDefinitionId;
    this.configuration = configuration;
  }

  public UUID pipelineDefinitionId() {
    return pipelineDefinitionId;
  }

  public String configuration() {
    return configuration;
  }
}
