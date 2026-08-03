package io.streamforge.controlplane.api;

import com.fasterxml.jackson.databind.JsonNode;

/** Validated configuration snapshots belonging to one immutable pipeline revision. */
public record PipelineConfigurationResponse(
    JsonNode input, JsonNode transform, JsonNode blueprint, JsonNode output) {}
