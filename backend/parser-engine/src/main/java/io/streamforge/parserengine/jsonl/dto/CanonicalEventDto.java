package io.streamforge.parserengine.jsonl.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** External JSON DTO; it is deliberately separate from the canonical domain envelope. */
public record CanonicalEventDto(
    EventMetadataDto metadata, InstrumentReferenceDto instrument, JsonNode payload) {}
