package io.streamforge.controlplane.api;

/** Public persistence request model; it intentionally does not expose JPA entities. */
public record CreatePipelineDefinitionRequest(
    String name,
    String inputConfiguration,
    String transformConfiguration,
    String outputConfiguration) {
  public CreatePipelineDefinitionRequest {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("pipeline name must be non-blank");
    }
  }
}
