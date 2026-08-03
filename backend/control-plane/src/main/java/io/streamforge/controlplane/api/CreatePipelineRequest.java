package io.streamforge.controlplane.api;

/** HTTP request for a pipeline definition and its initial revision. */
public record CreatePipelineRequest(
    String name, String description, PipelineConfigurationRequest configuration) {}
