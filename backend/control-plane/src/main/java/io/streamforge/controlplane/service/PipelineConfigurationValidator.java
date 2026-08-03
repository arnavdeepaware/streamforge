package io.streamforge.controlplane.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.streamforge.controlplane.api.FieldViolation;
import io.streamforge.controlplane.api.PipelineConfigurationRequest;
import io.streamforge.transform.blueprint.OutputBlueprintConfigException;
import io.streamforge.transform.blueprint.OutputBlueprintService;
import io.streamforge.transform.blueprint.OutputBlueprintValidationException;
import io.streamforge.transform.compile.CanonicalTransformationFields;
import io.streamforge.transform.compile.CompiledTransformation;
import io.streamforge.transform.compile.TransformationCompiler;
import io.streamforge.transform.compile.TransformationValidationException;
import io.streamforge.transform.config.TransformationConfigException;
import io.streamforge.transform.config.TransformationConfigParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Validates the typed, non-executable configuration snapshots accepted by pipeline APIs. */
@Component
public final class PipelineConfigurationValidator {
  private final ConfigurationValidator configurationValidator;
  private final ObjectMapper objectMapper;
  private final TransformationConfigParser transformParser = new TransformationConfigParser();
  private final TransformationCompiler transformCompiler = new TransformationCompiler();
  private final OutputBlueprintService blueprintService = new OutputBlueprintService();

  public PipelineConfigurationValidator(
      ConfigurationValidator configurationValidator, ObjectMapper objectMapper) {
    this.configurationValidator = configurationValidator;
    this.objectMapper = objectMapper;
  }

  /** Returns compact, validated JSON snapshots or a complete set of field errors. */
  public ValidatedPipelineConfiguration validate(PipelineConfigurationRequest request) {
    List<FieldViolation> errors = new ArrayList<>();
    if (request == null) {
      throw new ApiValidationException(List.of(new FieldViolation("configuration", "is required")));
    }

    String input = validateSection(request.input(), "input", true, errors);
    String transform = validateSection(request.transform(), "transform", false, errors);
    String blueprint = validateSection(request.blueprint(), "blueprint", false, errors);
    String output = validateSection(request.output(), "output", true, errors);

    CompiledTransformation compiledTransformation = null;
    if (transform != null) {
      try {
        compiledTransformation =
            transformCompiler.compile(
                transformParser.parse(transform), CanonicalTransformationFields.v1());
      } catch (TransformationConfigException exception) {
        errors.add(
            new FieldViolation(
                "configuration.transform" + exception.location().substring(1),
                exception.getMessage()));
      } catch (TransformationValidationException exception) {
        errors.add(
            new FieldViolation(
                "configuration.transform.operations[" + exception.operationIndex() + "]",
                exception.getMessage()));
      }
    }
    if (blueprint != null) {
      try {
        blueprintService.compile(blueprint, Optional.ofNullable(compiledTransformation));
      } catch (OutputBlueprintConfigException | OutputBlueprintValidationException exception) {
        String location =
            exception instanceof OutputBlueprintConfigException config
                ? config.location()
                : ((OutputBlueprintValidationException) exception).location();
        errors.add(
            new FieldViolation(
                "configuration.blueprint" + location.substring(1), exception.getMessage()));
      }
    }
    if (!errors.isEmpty()) {
      throw new ApiValidationException(errors);
    }
    return new ValidatedPipelineConfiguration(input, transform, blueprint, output);
  }

  private String validateSection(
      JsonNode section, String name, boolean requireType, List<FieldViolation> errors) {
    String path = "configuration." + name;
    if (section == null || section.isNull()) {
      errors.add(new FieldViolation(path, "is required"));
      return null;
    }
    if (!section.isObject()) {
      errors.add(new FieldViolation(path, "must be a JSON object"));
      return null;
    }
    if (requireType
        && (!section.hasNonNull("type")
            || !section.get("type").isTextual()
            || section.get("type").asText().isBlank())) {
      errors.add(new FieldViolation(path + ".type", "is required"));
    }
    try {
      return configurationValidator.validate(objectMapper.writeValueAsString(section), name);
    } catch (JsonProcessingException exception) {
      errors.add(new FieldViolation(path, "must be serializable JSON"));
    } catch (ConfigurationValidationException exception) {
      errors.add(new FieldViolation(path, exception.getMessage()));
    }
    return null;
  }

  /** JSON snapshots validated before they are persisted as a pipeline revision. */
  public record ValidatedPipelineConfiguration(
      String input, String transform, String blueprint, String output) {}
}
