package io.streamforge.controlplane.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Immutable JSON Schema revision returned by version one REST endpoints. */
public record SchemaRevisionResponse(
    UUID id, long revisionNumber, JsonNode document, Instant createdAt) {}
