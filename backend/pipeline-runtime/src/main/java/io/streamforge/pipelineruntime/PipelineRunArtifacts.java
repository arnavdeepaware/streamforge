package io.streamforge.pipelineruntime;

import java.nio.file.Path;
import java.util.UUID;

/** Server- or CLI-owned directory for immutable artifacts produced by one run. */
public record PipelineRunArtifacts(UUID runId, Path directory) {
  public PipelineRunArtifacts {
    if (runId == null || directory == null) {
      throw new IllegalArgumentException("run artifact fields must not be null");
    }
    directory = directory.toAbsolutePath().normalize();
  }

  public Path rawCapture() {
    return directory.resolve("raw-input.capture");
  }

  public Path rawCaptureManifest() {
    return directory.resolve("raw-input-manifest.json");
  }
}
