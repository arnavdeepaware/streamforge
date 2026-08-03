package io.streamforge.parserengine.csv;

/** One row-oriented outcome emitted by the CSV adapter. */
public sealed interface CsvEvent permits CsvCanonicalEvent, CsvError {

  /** Returns the one-based physical CSV record start row. */
  long rowNumber();

  /** Returns the raw CSV record before parsing; callers must bound any retained copy. */
  String sourceText();
}
