package io.streamforge.controlplane.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Server-produced views for one draft preview, including all non-executable validation failures.
 */
public record PipelinePreviewResponse(
    String status,
    JsonNode input,
    JsonNode transformed,
    JsonNode output,
    List<FieldViolation> errors) {
  public PipelinePreviewResponse {
    errors = List.copyOf(errors);
  }
}
