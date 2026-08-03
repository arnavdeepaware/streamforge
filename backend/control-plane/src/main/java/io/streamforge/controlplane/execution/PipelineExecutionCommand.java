package io.streamforge.controlplane.execution;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Immutable revision snapshot submitted to an execution backend. */
public record PipelineExecutionCommand(
    UUID runId,
    UUID pipelineId,
    long revisionNumber,
    JsonNode input,
    String transform,
    String blueprint,
    JsonNode output,
    JsonNode deadLetter) {}
