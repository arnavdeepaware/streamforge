package io.streamforge.parserengine.jsonl.dto;

/** External JSON DTO for canonical event metadata. */
public record EventMetadataDto(
    String eventId,
    CanonicalSchemaVersionDto schemaVersion,
    String source,
    String venue,
    Long exchangeTimestamp,
    Long receiveTimestamp,
    Long sequenceNumber,
    String rawEventReference) {}
