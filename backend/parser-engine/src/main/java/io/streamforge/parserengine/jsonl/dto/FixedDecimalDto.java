package io.streamforge.parserengine.jsonl.dto;

/** External JSON DTO for an exact mantissa and decimal scale. */
public record FixedDecimalDto(Long mantissa, Integer scale) {}
