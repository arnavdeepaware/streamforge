package io.streamforge.controlplane.persistence.entity;

import io.streamforge.controlplane.execution.PipelineRunState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Durable execution lifecycle record for one immutable pipeline revision. */
@Entity
@Table(name = "pipeline_runs")
public class PipelineRunEntity extends AuditedEntity {
  @Column(name = "pipeline_definition_id", nullable = false)
  private UUID pipelineDefinitionId;

  @Column(name = "pipeline_revision_id", nullable = false)
  private UUID pipelineRevisionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PipelineRunState state;

  @Column(name = "failure_summary", length = 512)
  private String failureSummary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "final_report", columnDefinition = "jsonb")
  private String finalReport;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "dead_letter_configuration", columnDefinition = "jsonb")
  private String deadLetterConfiguration;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "output_artifact_path", length = 1024)
  private String outputArtifactPath;

  @Column(name = "dead_letter_artifact_path", length = 1024)
  private String deadLetterArtifactPath;

  protected PipelineRunEntity() {}

  public PipelineRunEntity(
      UUID pipelineDefinitionId, UUID pipelineRevisionId, String deadLetterConfiguration) {
    this.pipelineDefinitionId = pipelineDefinitionId;
    this.pipelineRevisionId = pipelineRevisionId;
    this.deadLetterConfiguration = deadLetterConfiguration;
    state = PipelineRunState.CREATED;
  }

  public PipelineRunState state() {
    return state;
  }

  public UUID pipelineDefinitionId() {
    return pipelineDefinitionId;
  }

  public UUID pipelineRevisionId() {
    return pipelineRevisionId;
  }

  public String finalReport() {
    return finalReport;
  }

  public String failureSummary() {
    return failureSummary;
  }

  public String deadLetterConfiguration() {
    return deadLetterConfiguration;
  }

  public Instant startedAt() {
    return startedAt;
  }

  public Instant finishedAt() {
    return finishedAt;
  }

  public String outputArtifactPath() {
    return outputArtifactPath;
  }

  public String deadLetterArtifactPath() {
    return deadLetterArtifactPath;
  }

  public void transition(PipelineRunState target, String failure) {
    if (!state.canTransitionTo(target))
      throw new IllegalStateException(
          "illegal pipeline state transition: " + state + " to " + target);
    state = target;
    if (target == PipelineRunState.RUNNING) startedAt = Instant.now();
    if (!target.active()) finishedAt = Instant.now();
    if (failure != null && !failure.isBlank()) failureSummary = failure;
  }

  public void complete(String report, String outputArtifactPath, String deadLetterArtifactPath) {
    finalReport = report;
    this.outputArtifactPath = outputArtifactPath;
    this.deadLetterArtifactPath = deadLetterArtifactPath;
    transition(PipelineRunState.COMPLETED, null);
  }

  public void stop(String report, String deadLetterArtifactPath) {
    finalReport = report;
    this.deadLetterArtifactPath = deadLetterArtifactPath;
    transition(PipelineRunState.STOPPED, null);
  }

  public void fail(String report, String summary, String deadLetterArtifactPath) {
    finalReport = report;
    this.deadLetterArtifactPath = deadLetterArtifactPath;
    transition(PipelineRunState.FAILED, summary);
  }
}
