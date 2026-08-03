package io.streamforge.pipelineruntime;

/** Stable identity and version of a saved local pipeline configuration. */
public record PipelineIdentity(String pipelineId, String pipelineVersion) {
  public PipelineIdentity {
    if (pipelineId == null
        || pipelineId.isBlank()
        || pipelineVersion == null
        || pipelineVersion.isBlank()) {
      throw new IllegalArgumentException("pipeline identity and version must be non-blank");
    }
  }

  public static PipelineIdentity localDefault() {
    return new PipelineIdentity("local-pipeline", "1.0");
  }
}
