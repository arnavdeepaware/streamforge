package io.streamforge.controlplane.service;

import io.streamforge.controlplane.api.FieldViolation;
import java.util.List;

/** Validation failure that can be rendered as a version-one problem-details response. */
public final class ApiValidationException extends RuntimeException {
  private final List<FieldViolation> errors;

  public ApiValidationException(List<FieldViolation> errors) {
    super("request validation failed");
    if (errors == null || errors.isEmpty()) {
      throw new IllegalArgumentException("at least one field violation is required");
    }
    this.errors = List.copyOf(errors);
  }

  public List<FieldViolation> errors() {
    return errors;
  }
}
