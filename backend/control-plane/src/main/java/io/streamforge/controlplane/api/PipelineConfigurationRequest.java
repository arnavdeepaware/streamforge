package io.streamforge.controlplane.api;

import com.fasterxml.jackson.databind.JsonNode;

/** Typed configuration sections used to validate and create one immutable pipeline revision. */
public record PipelineConfigurationRequest(
    JsonNode input, JsonNode transform, JsonNode blueprint, JsonNode output) {}
