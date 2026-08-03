package io.streamforge.transform.blueprint;

/** Typed Java service result for rendering a sample event through a compiled blueprint. */
public sealed interface BlueprintPreviewResult
    permits BlueprintPreviewResult.Rendered, BlueprintPreviewResult.Failed {
  record Rendered(OutputBlueprintDocument document) implements BlueprintPreviewResult {}

  record Failed(BlueprintPreviewFailure failure) implements BlueprintPreviewResult {}
}
