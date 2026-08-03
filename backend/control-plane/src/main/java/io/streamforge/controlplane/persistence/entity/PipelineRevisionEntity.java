package io.streamforge.controlplane.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An immutable selection of input, transform, and output definitions for one revision number. */
@Entity
@Table(name = "pipeline_revisions")
public class PipelineRevisionEntity extends AuditedEntity {
  @Column(name = "pipeline_definition_id", nullable = false)
  private UUID pipelineDefinitionId;

  @Column(name = "revision_number", nullable = false)
  private long revisionNumber;

  @Column(name = "input_definition_id", nullable = false)
  private UUID inputDefinitionId;

  @Column(name = "transform_definition_id", nullable = false)
  private UUID transformDefinitionId;

  @Column(name = "output_definition_id", nullable = false)
  private UUID outputDefinitionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "blueprint_configuration", columnDefinition = "jsonb")
  private String blueprintConfiguration;

  protected PipelineRevisionEntity() {}

  public PipelineRevisionEntity(
      UUID pipelineDefinitionId,
      long revisionNumber,
      UUID inputDefinitionId,
      UUID transformDefinitionId,
      UUID outputDefinitionId) {
    this(
        pipelineDefinitionId,
        revisionNumber,
        inputDefinitionId,
        transformDefinitionId,
        outputDefinitionId,
        null);
  }

  public PipelineRevisionEntity(
      UUID pipelineDefinitionId,
      long revisionNumber,
      UUID inputDefinitionId,
      UUID transformDefinitionId,
      UUID outputDefinitionId,
      String blueprintConfiguration) {
    if (revisionNumber < 1) {
      throw new IllegalArgumentException("pipeline revision number must be positive");
    }
    this.pipelineDefinitionId = pipelineDefinitionId;
    this.revisionNumber = revisionNumber;
    this.inputDefinitionId = inputDefinitionId;
    this.transformDefinitionId = transformDefinitionId;
    this.outputDefinitionId = outputDefinitionId;
    this.blueprintConfiguration = blueprintConfiguration;
  }

  public UUID pipelineDefinitionId() {
    return pipelineDefinitionId;
  }

  public long revisionNumber() {
    return revisionNumber;
  }

  public UUID inputDefinitionId() {
    return inputDefinitionId;
  }

  public UUID transformDefinitionId() {
    return transformDefinitionId;
  }

  public UUID outputDefinitionId() {
    return outputDefinitionId;
  }

  public String blueprintConfiguration() {
    return blueprintConfiguration;
  }
}
