package io.streamforge.controlplane.api;

import java.util.UUID;

/** Public result returned after a definition and its first revision are durably created. */
public record PipelineDefinitionCreated(
    UUID pipelineDefinitionId, UUID revisionId, long revisionNumber) {}
