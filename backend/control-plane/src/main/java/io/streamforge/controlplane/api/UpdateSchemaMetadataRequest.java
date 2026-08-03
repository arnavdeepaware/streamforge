package io.streamforge.controlplane.api;

/** Mutable schema catalog metadata; schema documents are only changed through revisions. */
public record UpdateSchemaMetadataRequest(String name, String description) {}
