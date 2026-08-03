package io.streamforge.controlplane.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.streamforge.common.model.CanonicalEvent;
import io.streamforge.controlplane.api.CanonicalFieldResponse;
import io.streamforge.controlplane.api.FieldViolation;
import io.streamforge.controlplane.api.PipelinePreviewRequest;
import io.streamforge.controlplane.api.PipelinePreviewResponse;
import io.streamforge.parserengine.JsonLinesCanonicalEvent;
import io.streamforge.parserengine.JsonLinesError;
import io.streamforge.parserengine.JsonLinesEvent;
import io.streamforge.parserengine.JsonLinesInputAdapter;
import io.streamforge.parserengine.JsonLinesMode;
import io.streamforge.transform.blueprint.BlueprintPreviewResult;
import io.streamforge.transform.blueprint.CompiledOutputBlueprint;
import io.streamforge.transform.blueprint.OutputBlueprintConfigException;
import io.streamforge.transform.blueprint.OutputBlueprintService;
import io.streamforge.transform.blueprint.OutputBlueprintValidationException;
import io.streamforge.transform.compile.CanonicalTransformationFields;
import io.streamforge.transform.compile.CompiledTransformation;
import io.streamforge.transform.compile.TransformationCompiler;
import io.streamforge.transform.compile.TransformationValidationException;
import io.streamforge.transform.config.TransformationConfigException;
import io.streamforge.transform.config.TransformationConfigParser;
import io.streamforge.transform.execute.CanonicalEventDocument;
import io.streamforge.transform.execute.TransformationExecutor;
import io.streamforge.transform.execute.TransformationResult;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Executes the production declarative transform and blueprint engines for a single sample event.
 */
@Service
public class PipelinePreviewService {
  private final ObjectMapper mapper;
  private final TransformationConfigParser transformParser = new TransformationConfigParser();
  private final TransformationCompiler transformCompiler = new TransformationCompiler();
  private final OutputBlueprintService blueprintService = new OutputBlueprintService();

  public PipelinePreviewService(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** Returns the version-one canonical field catalog used for static reference validation. */
  public List<CanonicalFieldResponse> canonicalFields() {
    return CanonicalTransformationFields.v1().fields().values().stream()
        .map(
            field ->
                new CanonicalFieldResponse(
                    field.path().toString(), field.type().name(), field.protectedField()))
        .sorted(Comparator.comparing(CanonicalFieldResponse::path))
        .toList();
  }

  /** Compiles and previews a draft without saving configuration or starting a pipeline. */
  public PipelinePreviewResponse preview(PipelinePreviewRequest request) {
    List<FieldViolation> errors = new ArrayList<>();
    CanonicalEvent event = sampleEvent(request == null ? null : request.sampleEvent(), errors);
    if (event == null)
      return response("INVALID", NullNode.instance, NullNode.instance, NullNode.instance, errors);

    JsonNode input = mapper.valueToTree(CanonicalEventDocument.fromCanonicalEvent(event).root());
    CompiledTransformation transformation = transformation(request.transform(), errors);
    if (transformation == null)
      return response("INVALID", input, NullNode.instance, NullNode.instance, errors);
    CompiledOutputBlueprint blueprint = blueprint(request.blueprint(), transformation, errors);
    if (blueprint == null)
      return response("INVALID", input, NullNode.instance, NullNode.instance, errors);

    TransformationResult result = new TransformationExecutor(transformation).execute(event);
    if (result instanceof TransformationResult.Filtered) {
      return response("FILTERED", input, NullNode.instance, NullNode.instance, errors);
    }
    if (result instanceof TransformationResult.Failed failed) {
      errors.add(
          new FieldViolation(
              "transform." + failed.failure().fieldPath(), failed.failure().detail()));
      return response("INVALID", input, NullNode.instance, NullNode.instance, errors);
    }
    CanonicalEventDocument transformed = ((TransformationResult.Transformed) result).document();
    JsonNode transformedNode = mapper.valueToTree(transformed.root());
    BlueprintPreviewResult rendered =
        blueprintService.preview(blueprint, event, Optional.of(transformed));
    if (rendered instanceof BlueprintPreviewResult.Failed failed) {
      errors.add(
          new FieldViolation("blueprint" + failed.failure().location(), failed.failure().detail()));
      return response("INVALID", input, transformedNode, NullNode.instance, errors);
    }
    JsonNode output =
        mapper.valueToTree(((BlueprintPreviewResult.Rendered) rendered).document().root());
    return response("RENDERED", input, transformedNode, output, errors);
  }

  private CanonicalEvent sampleEvent(JsonNode sample, List<FieldViolation> errors) {
    if (sample == null || sample.isNull()) {
      errors.add(new FieldViolation("sampleEvent", "is required"));
      return null;
    }
    try {
      List<JsonLinesEvent> events = new ArrayList<>(1);
      new JsonLinesInputAdapter()
          .process(
              new StringReader(mapper.writeValueAsString(sample)),
              JsonLinesMode.FAIL_FAST,
              events::add);
      if (!events.isEmpty() && events.getFirst() instanceof JsonLinesCanonicalEvent valid)
        return valid.event();
      if (!events.isEmpty() && events.getFirst() instanceof JsonLinesError invalid) {
        errors.add(new FieldViolation("sampleEvent", invalid.detail()));
      } else errors.add(new FieldViolation("sampleEvent", "could not be parsed"));
    } catch (IOException exception) {
      errors.add(new FieldViolation("sampleEvent", "must be valid canonical JSON"));
    }
    return null;
  }

  private CompiledTransformation transformation(JsonNode node, List<FieldViolation> errors) {
    String json = section(node, "transform", errors);
    if (json == null) return null;
    try {
      return transformCompiler.compile(
          transformParser.parse(json), CanonicalTransformationFields.v1());
    } catch (TransformationConfigException exception) {
      errors.add(new FieldViolation("transform" + exception.location(), exception.getMessage()));
    } catch (TransformationValidationException exception) {
      errors.add(
          new FieldViolation(
              "transform.operations[" + exception.operationIndex() + "]", exception.getMessage()));
    }
    return null;
  }

  private CompiledOutputBlueprint blueprint(
      JsonNode node, CompiledTransformation transform, List<FieldViolation> errors) {
    String json = section(node, "blueprint", errors);
    if (json == null) return null;
    try {
      return blueprintService.compile(json, Optional.of(transform));
    } catch (OutputBlueprintConfigException | OutputBlueprintValidationException exception) {
      String location =
          exception instanceof OutputBlueprintConfigException config
              ? config.location()
              : ((OutputBlueprintValidationException) exception).location();
      errors.add(new FieldViolation("blueprint" + location, exception.getMessage()));
    }
    return null;
  }

  private String section(JsonNode node, String field, List<FieldViolation> errors) {
    if (node == null || !node.isObject()) {
      errors.add(new FieldViolation(field, "must be a JSON object"));
      return null;
    }
    try {
      return mapper.writeValueAsString(node);
    } catch (JsonProcessingException exception) {
      errors.add(new FieldViolation(field, "must be serializable JSON"));
      return null;
    }
  }

  private static PipelinePreviewResponse response(
      String status,
      JsonNode input,
      JsonNode transformed,
      JsonNode output,
      List<FieldViolation> errors) {
    return new PipelinePreviewResponse(status, input, transformed, output, errors);
  }
}
