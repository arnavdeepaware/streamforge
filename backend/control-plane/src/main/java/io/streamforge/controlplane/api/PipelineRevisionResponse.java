package io.streamforge.controlplane.api;

import java.time.Instant;
import java.util.UUID;

/** Immutable pipeline revision returned by version one REST endpoints. */
public record PipelineRevisionResponse(
    UUID id, long revisionNumber, PipelineConfigurationResponse configuration, Instant createdAt) {}
