package io.streamforge.pipelineruntime.deadletter;

import io.streamforge.pipelineruntime.PipelineStage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable, versioned local failure record with a deterministic identifier. */
public record DeadLetterRecord(
    String failureId,
    String pipelineId,
    String pipelineVersion,
    PipelineStage stage,
    String sourceLocation,
    Optional<String> eventId,
    DeadLetterCategory category,
    String safeMessage,
    Optional<DeadLetterPayload> payload,
    Instant timestamp,
    Retryability retryability,
    String canonicalSchemaVersion,
    String pipelineSchemaVersion) {
  public static final String RECORD_SCHEMA_VERSION = "1.0";

  public DeadLetterRecord {
    if (failureId == null
        || pipelineId == null
        || pipelineId.isBlank()
        || pipelineVersion == null
        || pipelineVersion.isBlank()
        || stage == null
        || sourceLocation == null
        || sourceLocation.isBlank()
        || eventId == null
        || category == null
        || safeMessage == null
        || safeMessage.isBlank()
        || payload == null
        || timestamp == null
        || retryability == null
        || canonicalSchemaVersion == null
        || pipelineSchemaVersion == null) {
      throw new IllegalArgumentException("dead-letter record fields must be present and valid");
    }
  }

  /** Creates a record whose ID is stable across reprocessing of the same classified failure. */
  public static DeadLetterRecord create(
      String pipelineId,
      String pipelineVersion,
      PipelineStage stage,
      String sourceLocation,
      Optional<String> eventId,
      DeadLetterCategory category,
      String safeMessage,
      Optional<DeadLetterPayload> payload,
      Instant timestamp,
      Retryability retryability,
      String canonicalSchemaVersion,
      String pipelineSchemaVersion) {
    String id =
        identifier(
            pipelineId,
            pipelineVersion,
            stage,
            sourceLocation,
            eventId,
            category,
            safeMessage,
            canonicalSchemaVersion,
            pipelineSchemaVersion);
    return new DeadLetterRecord(
        id,
        pipelineId,
        pipelineVersion,
        stage,
        sourceLocation,
        eventId,
        category,
        safeMessage,
        payload,
        timestamp,
        retryability,
        canonicalSchemaVersion,
        pipelineSchemaVersion);
  }

  /** Converts the record into the JSON-safe shape written by the local JSONL store. */
  public Map<String, Object> asJson() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("canonicalSchemaVersion", canonicalSchemaVersion);
    fields.put("category", category.name());
    eventId.ifPresent(value -> fields.put("eventId", value));
    fields.put("failureId", failureId);
    fields.put("pipelineId", pipelineId);
    fields.put("pipelineSchemaVersion", pipelineSchemaVersion);
    fields.put("pipelineVersion", pipelineVersion);
    payload.ifPresent(value -> fields.put("payload", value.asJson()));
    fields.put("retryability", retryability.name());
    fields.put("safeMessage", safeMessage);
    fields.put("stage", stage.name());
    fields.put("sourceLocation", sourceLocation);
    fields.put("timestamp", timestamp.toString());
    return Map.copyOf(fields);
  }

  private static String identifier(
      String pipelineId,
      String pipelineVersion,
      PipelineStage stage,
      String sourceLocation,
      Optional<String> eventId,
      DeadLetterCategory category,
      String safeMessage,
      String canonicalSchemaVersion,
      String pipelineSchemaVersion) {
    String canonical =
        String.join(
            "\u0000",
            "streamforge:dead-letter:v1",
            pipelineId,
            pipelineVersion,
            stage.name(),
            sourceLocation,
            eventId.orElse(""),
            category.name(),
            safeMessage,
            canonicalSchemaVersion,
            pipelineSchemaVersion);
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
    }
  }
}
