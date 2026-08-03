package io.streamforge.transform.blueprint;

/** Actionable raw blueprint configuration failure with a JSON location. */
public final class OutputBlueprintConfigException extends Exception {
  private final BlueprintConfigErrorCode code;
  private final String location;

  OutputBlueprintConfigException(BlueprintConfigErrorCode code, String location, String detail) {
    super(detail);
    this.code = code;
    this.location = location;
  }

  public BlueprintConfigErrorCode code() {
    return code;
  }

  public String location() {
    return location;
  }
}
