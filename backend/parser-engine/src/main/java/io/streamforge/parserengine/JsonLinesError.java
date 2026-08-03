package io.streamforge.parserengine;

/** A typed validation or parsing failure tied to one source line. */
public record JsonLinesError(long lineNumber, JsonLinesErrorReason reason, String detail)
    implements JsonLinesEvent {

  public JsonLinesError {
    if (lineNumber < 1 || reason == null || detail == null || detail.isBlank()) {
      throw new IllegalArgumentException("line error requires a positive line, reason, and detail");
    }
  }
}
