package io.streamforge.controlplane.api;

import java.time.Instant;
import java.util.UUID;

/** Schema identity, mutable catalog metadata, and its latest immutable revision. */
public record SchemaDefinitionResponse(
    UUID id,
    String name,
    String description,
    boolean archived,
    long version,
    Instant createdAt,
    Instant updatedAt,
    SchemaRevisionResponse latestRevision) {}
