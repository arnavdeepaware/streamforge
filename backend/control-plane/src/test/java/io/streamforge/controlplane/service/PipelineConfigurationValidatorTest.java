package io.streamforge.controlplane.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.streamforge.controlplane.api.PipelineConfigurationRequest;
import org.junit.jupiter.api.Test;

class PipelineConfigurationValidatorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final PipelineConfigurationValidator validator =
      new PipelineConfigurationValidator(new ConfigurationValidator(objectMapper), objectMapper);

  @Test
  void compilesDeclarativeTransformAndBlueprintBeforePersistence() throws Exception {
    PipelineConfigurationValidator.ValidatedPipelineConfiguration configuration =
        validator.validate(validConfiguration());

    assertThat(configuration.input()).contains("STP_BINARY");
    assertThat(configuration.transform()).contains("rename");
    assertThat(configuration.blueprint()).contains("instrument.symbol");
    assertThat(configuration.output()).contains("JSONL");
  }

  @Test
  void reportsFieldLevelConfigurationErrorsWithoutAcceptingExecutableRules() throws Exception {
    PipelineConfigurationRequest unsafe =
        new PipelineConfigurationRequest(
            objectMapper.readTree("{\"type\":\"STP_BINARY\"}"),
            objectMapper.readTree(
                "{\"schemaVersion\":\"1.0\",\"operations\":[{\"op\":\"javascript\"}]}"),
            objectMapper.readTree(
                "{\"schemaVersion\":\"1.0\",\"output\":{\"kind\":\"object\",\"fields\":{}}}"),
            objectMapper.readTree("{\"type\":\"JSONL\"}"));

    assertThatThrownBy(() -> validator.validate(unsafe))
        .isInstanceOfSatisfying(
            ApiValidationException.class,
            exception ->
                assertThat(exception.errors())
                    .anySatisfy(
                        error -> {
                          assertThat(error.field())
                              .isEqualTo("configuration.transform.operations[0].op");
                          assertThat(error.message()).contains("unknown operation");
                        }));
  }

  private PipelineConfigurationRequest validConfiguration() throws Exception {
    return new PipelineConfigurationRequest(
        objectMapper.readTree("{\"type\":\"STP_BINARY\"}"),
        objectMapper.readTree(
            "{\"schemaVersion\":\"1.0\",\"operations\":[{\"op\":\"rename\",\"from\":\"instrument.symbol\",\"to\":\"instrument.ticker\"}]}"),
        objectMapper.readTree(
            "{\"schemaVersion\":\"1.0\",\"output\":{\"kind\":\"object\",\"fields\":{\"symbol\":{\"kind\":\"reference\",\"source\":\"canonical\",\"path\":\"instrument.symbol\"}}}}"),
        objectMapper.readTree("{\"type\":\"JSONL\"}"));
  }
}
