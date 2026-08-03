package io.streamforge.controlplane.api;

/** One canonical field available for declarative mapping and blueprint references. */
public record CanonicalFieldResponse(String path, String type, boolean protectedField) {}
