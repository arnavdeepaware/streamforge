package io.streamforge.transform.config;

/** Typed failure raised before an untrusted transformation configuration is accepted. */
public final class TransformationConfigException extends Exception {

  private final ConfigurationErrorCode code;
  private final String location;

  public TransformationConfigException(
      ConfigurationErrorCode code, String location, String message) {
    super(message);
    if (code == null || location == null || location.isBlank()) {
      throw new IllegalArgumentException("configuration error code and location are required");
    }
    this.code = code;
    this.location = location;
  }

  public TransformationConfigException(
      ConfigurationErrorCode code, String location, String message, Throwable cause) {
    super(message, cause);
    if (code == null || location == null || location.isBlank()) {
      throw new IllegalArgumentException("configuration error code and location are required");
    }
    this.code = code;
    this.location = location;
  }

  public ConfigurationErrorCode code() {
    return code;
  }

  public String location() {
    return location;
  }
}
