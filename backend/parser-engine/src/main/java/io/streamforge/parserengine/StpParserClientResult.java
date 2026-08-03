package io.streamforge.parserengine;

/** Counts the ordered parse results observed while reading one TCP connection. */
public record StpParserClientResult(long parsedFrames, long parseFailures) {

  public StpParserClientResult {
    if (parsedFrames < 0 || parseFailures < 0) {
      throw new IllegalArgumentException("result counts must not be negative");
    }
  }
}
