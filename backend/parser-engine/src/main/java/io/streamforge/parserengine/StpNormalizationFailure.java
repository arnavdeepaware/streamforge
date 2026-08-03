package io.streamforge.parserengine;

/** Typed, non-transport failure produced when an STP frame cannot become a canonical event. */
public record StpNormalizationFailure(StpNormalizationFailureReason reason, String detail)
    implements StpNormalizationResult {

  public StpNormalizationFailure {
    if (reason == null || detail == null || detail.isBlank()) {
      throw new IllegalArgumentException("normalization failure reason and detail must be present");
    }
  }
}
