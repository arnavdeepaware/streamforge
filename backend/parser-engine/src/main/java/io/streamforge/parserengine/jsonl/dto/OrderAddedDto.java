package io.streamforge.parserengine.jsonl.dto;

/** External JSON DTO for an order-added payload. */
public record OrderAddedDto(
    String type, Long orderId, String side, Long quantity, FixedDecimalDto price) {}
