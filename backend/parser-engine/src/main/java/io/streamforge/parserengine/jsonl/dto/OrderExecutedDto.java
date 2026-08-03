package io.streamforge.parserengine.jsonl.dto;

/** External JSON DTO for an order-executed payload. */
public record OrderExecutedDto(String type, Long orderId, Long executedQuantity) {}
