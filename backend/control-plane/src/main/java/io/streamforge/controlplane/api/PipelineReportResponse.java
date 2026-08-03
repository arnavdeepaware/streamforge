package io.streamforge.controlplane.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.streamforge.controlplane.execution.PipelineRunState;
import java.util.UUID;

/** Final report payload retained for a completed, stopped, or failed pipeline run. */
public record PipelineReportResponse(UUID runId, PipelineRunState state, JsonNode report) {}
