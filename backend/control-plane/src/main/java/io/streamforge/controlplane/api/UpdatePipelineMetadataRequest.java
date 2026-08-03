package io.streamforge.controlplane.api;

/** Mutable metadata only; revision configuration is never updated in place. */
public record UpdatePipelineMetadataRequest(String name, String description) {}
