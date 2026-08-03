package io.streamforge.parserengine.csv;

/** Counts records observed during one CSV adapter run. */
public record CsvProcessingResult(long rowsRead, long eventsProduced, long errorsReported) {

  public CsvProcessingResult {
    if (rowsRead < 0 || eventsProduced < 0 || errorsReported < 0) {
      throw new IllegalArgumentException("CSV counts must not be negative");
    }
  }
}
