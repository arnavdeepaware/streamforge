package io.streamforge.controlplane.api;

import io.streamforge.controlplane.execution.PipelineRunState;
import java.time.Instant;
import java.util.UUID;

/** Current or final state of one pipeline execution. */
public record PipelineRunResponse(
    UUID runId,
    UUID pipelineId,
    UUID revisionId,
    PipelineRunState state,
    String failureSummary,
    Instant startedAt,
    Instant finishedAt) {}
