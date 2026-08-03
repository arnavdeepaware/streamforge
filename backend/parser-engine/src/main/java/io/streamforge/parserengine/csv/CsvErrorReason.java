package io.streamforge.parserengine.csv;

/** Stable classification for one CSV record or configuration failure. */
public enum CsvErrorReason {
  INVALID_CONFIGURATION,
  MISSING_COLUMN,
  MALFORMED_ROW,
  INVALID_TIMESTAMP,
  INVALID_SYMBOL,
  INVALID_VENUE,
  INVALID_DECIMAL,
  INVALID_QUANTITY,
  INVALID_SIDE,
  NORMALIZATION_ERROR
}
