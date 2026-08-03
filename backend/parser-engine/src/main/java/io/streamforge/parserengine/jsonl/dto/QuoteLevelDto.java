package io.streamforge.parserengine.jsonl.dto;

/** External JSON DTO for one quote level. */
public record QuoteLevelDto(FixedDecimalDto price, Long quantity) {}
