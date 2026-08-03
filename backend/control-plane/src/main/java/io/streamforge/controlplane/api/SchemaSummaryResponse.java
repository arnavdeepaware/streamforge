package io.streamforge.controlplane.api;

import java.time.Instant;
import java.util.UUID;

/** List representation for a schema definition. */
public record SchemaSummaryResponse(
    UUID id,
    String name,
    String description,
    boolean archived,
    long latestRevisionNumber,
    Instant createdAt,
    Instant updatedAt) {}
