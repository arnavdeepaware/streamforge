package io.streamforge.parserengine;

/** Counts records observed by one JSON Lines adapter run. */
public record JsonLinesProcessingResult(long linesRead, long eventsProduced, long errorsReported) {

  public JsonLinesProcessingResult {
    if (linesRead < 0 || eventsProduced < 0 || errorsReported < 0) {
      throw new IllegalArgumentException("JSON Lines counts must not be negative");
    }
  }
}
