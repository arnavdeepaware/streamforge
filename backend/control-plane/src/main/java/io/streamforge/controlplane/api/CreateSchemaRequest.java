package io.streamforge.controlplane.api;

import com.fasterxml.jackson.databind.JsonNode;

/** HTTP request for a schema definition and its first immutable document revision. */
public record CreateSchemaRequest(String name, String description, JsonNode document) {}
