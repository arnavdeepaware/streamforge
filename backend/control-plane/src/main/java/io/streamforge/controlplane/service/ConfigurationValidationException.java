package io.streamforge.controlplane.service;

/**
 * Raised when a persisted control-plane configuration is invalid or contains credential material.
 */
public final class ConfigurationValidationException extends IllegalArgumentException {
  public ConfigurationValidationException(String message) {
    super(message);
  }
}
