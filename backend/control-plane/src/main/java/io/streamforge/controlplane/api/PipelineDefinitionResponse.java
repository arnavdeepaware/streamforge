package io.streamforge.controlplane.api;

import java.time.Instant;
import java.util.UUID;

/** Pipeline identity, mutable metadata, and its latest immutable revision. */
public record PipelineDefinitionResponse(
    UUID id,
    String name,
    String description,
    boolean archived,
    long version,
    Instant createdAt,
    Instant updatedAt,
    PipelineRevisionResponse latestRevision) {}
