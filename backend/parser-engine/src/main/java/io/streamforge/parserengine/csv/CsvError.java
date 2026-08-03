package io.streamforge.parserengine.csv;

/** A typed CSV failure tied to the physical record where it was detected. */
public record CsvError(long rowNumber, CsvErrorReason reason, String detail) implements CsvEvent {

  public CsvError {
    if (rowNumber < 1 || reason == null || detail == null || detail.isBlank()) {
      throw new IllegalArgumentException("CSV error requires a positive row, reason, and detail");
    }
  }
}
