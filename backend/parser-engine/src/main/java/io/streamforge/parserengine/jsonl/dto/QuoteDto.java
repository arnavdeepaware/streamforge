package io.streamforge.parserengine.jsonl.dto;

/** External JSON DTO for a one- or two-sided quote payload. */
public record QuoteDto(String type, QuoteLevelDto bid, QuoteLevelDto ask) {}
