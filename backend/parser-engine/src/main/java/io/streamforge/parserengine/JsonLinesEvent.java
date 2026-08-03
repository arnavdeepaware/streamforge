package io.streamforge.parserengine;

/** One line-oriented result emitted by the JSON Lines adapter. */
public sealed interface JsonLinesEvent permits JsonLinesCanonicalEvent, JsonLinesError {

  /** Returns the one-based source line number associated with this result. */
  long lineNumber();
}
