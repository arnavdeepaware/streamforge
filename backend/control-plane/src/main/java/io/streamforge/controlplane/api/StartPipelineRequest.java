package io.streamforge.controlplane.api;

import com.fasterxml.jackson.databind.JsonNode;

/** Optional local execution settings for starting the latest immutable pipeline revision. */
public record StartPipelineRequest(JsonNode deadLetter) {}
