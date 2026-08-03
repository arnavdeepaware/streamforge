package io.streamforge.parserengine.jsonl.dto;

/** External JSON DTO for an order-cancelled payload. */
public record OrderCancelledDto(String type, Long orderId, Long cancelledQuantity) {}
