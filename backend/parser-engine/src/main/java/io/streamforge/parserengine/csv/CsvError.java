package io.streamforge.parserengine.csv;

/** A typed CSV failure tied to the physical record where it was detected. */
public record CsvError(long rowNumber, CsvErrorReason reason, String detail, String sourceText)
    implements CsvEvent {

  public CsvError(long rowNumber, CsvErrorReason reason, String detail) {
    this(rowNumber, reason, detail, "");
  }

  public CsvError {
    if (rowNumber < 1
        || reason == null
        || detail == null
        || detail.isBlank()
        || sourceText == null) {
      throw new IllegalArgumentException(
          "CSV error requires a positive row, reason, detail, and non-null source text");
    }
  }
}
