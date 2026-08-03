package io.streamforge.parserengine.csv;

import io.streamforge.common.model.CanonicalEvent;

/** A successfully normalized canonical trade from one CSV record. */
public record CsvCanonicalEvent(long rowNumber, CanonicalEvent event, String sourceText)
    implements CsvEvent {

  public CsvCanonicalEvent(long rowNumber, CanonicalEvent event) {
    this(rowNumber, event, "");
  }

  public CsvCanonicalEvent {
    if (rowNumber < 1 || event == null || sourceText == null) {
      throw new IllegalArgumentException(
          "row number must be positive, event must be present, and source text must not be null");
    }
  }
}
