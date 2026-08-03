package io.streamforge.controlplane.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SchemaDocumentValidatorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final SchemaDocumentValidator validator = new SchemaDocumentValidator(objectMapper);

  @Test
  void acceptsAJsonSchemaDocumentWithAnExplicitDialect() throws Exception {
    String document =
        validator.validate(
            objectMapper.readTree(
                "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\"}"));

    assertThat(document).contains("$schema");
  }

  @Test
  void rejectsDocumentsWithoutAnExplicitSchemaDialect() throws Exception {
    assertThatThrownBy(() -> validator.validate(objectMapper.readTree("{\"type\":\"object\"}")))
        .isInstanceOfSatisfying(
            ApiValidationException.class,
            exception ->
                assertThat(exception.errors())
                    .containsExactly(
                        new io.streamforge.controlplane.api.FieldViolation(
                            "document.$schema",
                            "is required to identify the JSON Schema dialect")));
  }
}
