package io.streamforge.parserengine;

/** A typed validation or parsing failure tied to one source line. */
public record JsonLinesError(
    long lineNumber, JsonLinesErrorReason reason, String detail, String sourceText)
    implements JsonLinesEvent {

  public JsonLinesError(long lineNumber, JsonLinesErrorReason reason, String detail) {
    this(lineNumber, reason, detail, "");
  }

  public JsonLinesError {
    if (lineNumber < 1
        || reason == null
        || detail == null
        || detail.isBlank()
        || sourceText == null) {
      throw new IllegalArgumentException(
          "line error requires a positive line, reason, detail, and non-null source text");
    }
  }
}
