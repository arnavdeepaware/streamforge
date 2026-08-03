package io.streamforge.transform.blueprint;

/** Data-dependent preview failure that does not affect subsequent sample events. */
public record BlueprintPreviewFailure(String location, String detail) {
  public BlueprintPreviewFailure {
    if (location == null || location.isBlank() || detail == null || detail.isBlank())
      throw new IllegalArgumentException("preview failure must include location and detail");
  }
}
