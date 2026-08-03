package io.streamforge.controlplane.api;

import java.util.List;

/** Result of validation-only API calls that never persist configuration. */
public record ValidationResult(boolean valid, List<FieldViolation> errors) {
  public ValidationResult {
    errors = List.copyOf(errors);
  }
}
