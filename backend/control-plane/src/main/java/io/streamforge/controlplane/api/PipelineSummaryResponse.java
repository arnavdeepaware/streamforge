package io.streamforge.controlplane.api;

import java.time.Instant;
import java.util.UUID;

/** List representation for a pipeline definition. */
public record PipelineSummaryResponse(
    UUID id,
    String name,
    String description,
    boolean archived,
    long latestRevisionNumber,
    Instant createdAt,
    Instant updatedAt) {}
