package io.streamforge.controlplane.api;

import com.fasterxml.jackson.databind.JsonNode;

/** HTTP request for a fresh immutable document revision of an existing schema. */
public record CreateSchemaRevisionRequest(JsonNode document) {}
