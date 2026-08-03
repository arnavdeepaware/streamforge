package io.streamforge.parserengine;

/** Outcome of normalizing one decoded STP frame into the canonical event model. */
public sealed interface StpNormalizationResult
    permits NormalizedStpEvent, StpNormalizationFailure {}
