package io.streamforge.parserengine;

/** Error handling policy for the streaming JSON Lines input adapter. */
public enum JsonLinesMode {
  FAIL_FAST,
  CONTINUE_WITH_ERRORS
}
