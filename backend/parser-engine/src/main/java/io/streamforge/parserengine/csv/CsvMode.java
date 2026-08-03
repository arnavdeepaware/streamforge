package io.streamforge.parserengine.csv;

/** Error handling policy for CSV ingestion. */
public enum CsvMode {
  FAIL_FAST,
  CONTINUE_WITH_ERRORS
}
