package io.streamforge.parserengine;

import io.streamforge.common.model.CanonicalEvent;

/** A successfully parsed and validated canonical event from one JSON Lines record. */
public record JsonLinesCanonicalEvent(long lineNumber, CanonicalEvent event, String sourceText)
    implements JsonLinesEvent {

  public JsonLinesCanonicalEvent(long lineNumber, CanonicalEvent event) {
    this(lineNumber, event, "");
  }

  public JsonLinesCanonicalEvent {
    if (lineNumber < 1 || event == null || sourceText == null) {
      throw new IllegalArgumentException(
          "line number must be positive, event must be present, and source text must not be null");
    }
  }
}
