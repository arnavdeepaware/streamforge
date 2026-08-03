package io.streamforge.controlplane.api;

import com.fasterxml.jackson.databind.JsonNode;

/** One canonical sample event and the declarative transform and blueprint drafts to preview. */
public record PipelinePreviewRequest(
    JsonNode sampleEvent, JsonNode transform, JsonNode blueprint) {}
