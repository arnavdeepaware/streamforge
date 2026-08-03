package io.streamforge.controlplane.api;

/** One machine-readable field validation failure returned in API problem details. */
public record FieldViolation(String field, String message) {}
