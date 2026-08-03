package io.streamforge.parserengine;

/** One line-oriented result emitted by the JSON Lines adapter. */
public sealed interface JsonLinesEvent permits JsonLinesCanonicalEvent, JsonLinesError {

  /** Returns the one-based source line number associated with this result. */
  long lineNumber();

  /** Returns the source line before parsing; callers must bound any retained copy. */
  String sourceText();
}
