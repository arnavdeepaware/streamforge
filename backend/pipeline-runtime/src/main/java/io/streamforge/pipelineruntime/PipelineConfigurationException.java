package io.streamforge.pipelineruntime;

/** Checked diagnostic for a saved pipeline configuration that cannot be activated. */
public final class PipelineConfigurationException extends Exception {
  private final String location;

  PipelineConfigurationException(String location, String detail, Throwable cause) {
    super(detail, cause);
    this.location = location;
  }

  /** Returns the configuration path or JSON location associated with this error. */
  public String location() {
    return location;
  }
}
