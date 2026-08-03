package io.streamforge.parserengine.jsonl.dto;

/** External JSON DTO for a trade payload. */
public record TradeDto(
    String type, Long tradeId, String aggressorSide, Long quantity, FixedDecimalDto price) {}
