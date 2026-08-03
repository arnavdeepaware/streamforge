package io.streamforge.transform.execute;

import io.streamforge.common.model.CanonicalEvent;

/** One non-throwing result of executing a compiled transformation against a canonical event. */
public sealed interface TransformationResult
    permits TransformationResult.Transformed,
        TransformationResult.Filtered,
        TransformationResult.Failed {

  /** A successful, immutable document view produced from the input event. */
  record Transformed(CanonicalEventDocument document) implements TransformationResult {
    public Transformed {
      if (document == null) {
        throw new IllegalArgumentException("transformed document must not be null");
      }
    }
  }

  /** A successfully evaluated filter that rejected the immutable input event. */
  record Filtered(CanonicalEvent sourceEvent) implements TransformationResult {
    public Filtered {
      if (sourceEvent == null) {
        throw new IllegalArgumentException("filtered source event must not be null");
      }
    }
  }

  /** A per-event failure with operation context; callers can continue with subsequent events. */
  record Failed(TransformationFailure failure) implements TransformationResult {
    public Failed {
      if (failure == null) {
        throw new IllegalArgumentException("transformation failure must not be null");
      }
    }
  }
}
