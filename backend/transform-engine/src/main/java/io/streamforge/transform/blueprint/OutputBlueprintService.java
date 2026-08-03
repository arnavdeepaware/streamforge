package io.streamforge.transform.blueprint;

import io.streamforge.common.model.CanonicalEvent;
import io.streamforge.transform.compile.CanonicalTransformationFields;
import io.streamforge.transform.compile.CompiledTransformation;
import io.streamforge.transform.execute.CanonicalEventDocument;
import java.util.Optional;

/** Java service-layer API for startup compilation and safe single-event blueprint previews. */
public final class OutputBlueprintService {
  private final OutputBlueprintParser parser = new OutputBlueprintParser();
  private final OutputBlueprintCompiler compiler = new OutputBlueprintCompiler();
  private final OutputBlueprintRenderer renderer = new OutputBlueprintRenderer();
  private final OutputBlueprintLimits limits;

  public OutputBlueprintService() {
    this(OutputBlueprintLimits.DEFAULT);
  }

  public OutputBlueprintService(OutputBlueprintLimits limits) {
    if (limits == null) throw new IllegalArgumentException("blueprint limits must not be null");
    this.limits = limits;
  }

  public CompiledOutputBlueprint compile(
      String json, Optional<CompiledTransformation> transformation)
      throws OutputBlueprintConfigException, OutputBlueprintValidationException {
    return compiler.compile(
        parser.parse(json),
        CanonicalTransformationFields.v1(),
        transformation.map(CompiledTransformation::outputSchema),
        limits);
  }

  public BlueprintPreviewResult preview(
      CompiledOutputBlueprint blueprint,
      CanonicalEvent event,
      Optional<CanonicalEventDocument> transformed) {
    if (blueprint == null || event == null || transformed == null)
      throw new IllegalArgumentException(
          "blueprint event and transformed document option must not be null");
    return renderer.render(
        blueprint, CanonicalEventDocument.fromCanonicalEvent(event), transformed);
  }
}
