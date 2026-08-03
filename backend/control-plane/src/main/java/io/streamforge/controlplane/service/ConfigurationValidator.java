package io.streamforge.controlplane.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Validates JSON configuration structure and prevents definition records from holding credentials.
 */
@Component
public final class ConfigurationValidator {
  private final ObjectMapper objectMapper;

  public ConfigurationValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Returns compact JSON for persistence after validating an object-only configuration. */
  public String validate(String configuration, String definitionType) {
    if (configuration == null || configuration.isBlank()) {
      throw new ConfigurationValidationException(definitionType + " configuration is required");
    }
    try {
      JsonNode root = objectMapper.readTree(configuration);
      if (root == null || !root.isObject()) {
        throw new ConfigurationValidationException(
            definitionType + " configuration must be a JSON object");
      }
      rejectCredentials(root);
      return objectMapper.writeValueAsString(root);
    } catch (JsonProcessingException exception) {
      throw new ConfigurationValidationException(
          definitionType + " configuration must be valid JSON");
    }
  }

  private static void rejectCredentials(JsonNode node) {
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (credentialField(field.getKey())) {
          throw new ConfigurationValidationException(
              "credential-like fields must be supplied through runtime environment configuration");
        }
        rejectCredentials(field.getValue());
      }
    } else if (node.isArray()) {
      for (JsonNode item : node) {
        rejectCredentials(item);
      }
    }
  }

  private static boolean credentialField(String fieldName) {
    String normalized = fieldName.toLowerCase(Locale.ROOT);
    return normalized.contains("password")
        || normalized.contains("secret")
        || normalized.contains("token")
        || normalized.contains("api_key")
        || normalized.contains("apikey")
        || normalized.contains("credential");
  }
}
