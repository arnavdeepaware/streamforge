package io.streamforge.controlplane.execution;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
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
    Optional<LocalDeadLetterOptions> deadLetter) {
  public PipelineExecutionCommand {
    if (runId == null
        || pipelineId == null
        || revisionNumber < 1
        || input == null
        || output == null
        || deadLetter == null) {
      throw new IllegalArgumentException("pipeline execution command fields are invalid");
    }
  }
}
