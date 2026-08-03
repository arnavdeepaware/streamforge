package io.streamforge.controlplane.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A validated, credential-free input configuration owned by a pipeline definition. */
@Entity
@Table(name = "input_definitions")
public class InputDefinitionEntity extends AuditedEntity {
  @Column(name = "pipeline_definition_id", nullable = false)
  private UUID pipelineDefinitionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String configuration;

  protected InputDefinitionEntity() {}

  public InputDefinitionEntity(UUID pipelineDefinitionId, String configuration) {
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
