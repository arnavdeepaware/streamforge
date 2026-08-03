package io.streamforge.controlplane.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.streamforge.controlplane.api.FieldViolation;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Performs the structural validation appropriate before storing a versioned JSON Schema document.
 */
@Component
public final class SchemaDocumentValidator {
  private final ObjectMapper objectMapper;

  public SchemaDocumentValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Returns canonical JSON after requiring an explicit JSON Schema dialect and root type. */
  public String validate(JsonNode document) {
    if (document == null || document.isNull()) {
      throw failure("document", "is required");
    }
    if (!document.isObject()) {
      throw failure("document", "must be a JSON object");
    }
    if (!document.hasNonNull("$schema")
        || !document.get("$schema").isTextual()
        || document.get("$schema").asText().isBlank()) {
      throw failure("document.$schema", "is required to identify the JSON Schema dialect");
    }
    try {
      return objectMapper.writeValueAsString(document);
    } catch (JsonProcessingException exception) {
      throw failure("document", "must be serializable JSON");
    }
  }

  private static ApiValidationException failure(String field, String message) {
    return new ApiValidationException(List.of(new FieldViolation(field, message)));
  }
}
