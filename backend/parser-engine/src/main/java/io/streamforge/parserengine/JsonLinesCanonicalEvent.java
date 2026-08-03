package io.streamforge.parserengine;

import io.streamforge.common.model.CanonicalEvent;

/** A successfully parsed and validated canonical event from one JSON Lines record. */
public record JsonLinesCanonicalEvent(long lineNumber, CanonicalEvent event)
    implements JsonLinesEvent {

  public JsonLinesCanonicalEvent {
    if (lineNumber < 1 || event == null) {
      throw new IllegalArgumentException("line number must be positive and event must be present");
    }
  }
}
