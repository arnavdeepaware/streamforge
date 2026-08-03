package io.streamforge.controlplane.api;

/** HTTP request for a fresh immutable revision of an existing pipeline. */
public record CreatePipelineRevisionRequest(PipelineConfigurationRequest configuration) {}
