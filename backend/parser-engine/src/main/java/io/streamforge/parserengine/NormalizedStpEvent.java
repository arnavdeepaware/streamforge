package io.streamforge.parserengine;

import io.streamforge.common.model.CanonicalEvent;

/** Successful STP normalization result. */
public record NormalizedStpEvent(CanonicalEvent event) implements StpNormalizationResult {

  public NormalizedStpEvent {
    if (event == null) {
      throw new IllegalArgumentException("canonical event must not be null");
    }
  }
}
