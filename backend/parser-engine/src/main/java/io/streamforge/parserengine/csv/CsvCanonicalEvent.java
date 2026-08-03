package io.streamforge.parserengine.csv;

import io.streamforge.common.model.CanonicalEvent;

/** A successfully normalized canonical trade from one CSV record. */
public record CsvCanonicalEvent(long rowNumber, CanonicalEvent event) implements CsvEvent {

  public CsvCanonicalEvent {
    if (rowNumber < 1 || event == null) {
      throw new IllegalArgumentException("row number must be positive and event must be present");
    }
  }
}
