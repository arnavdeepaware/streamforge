package io.streamforge.transform.blueprint;

/** Actionable semantic blueprint failure tied to one output location. */
public final class OutputBlueprintValidationException extends Exception {
  private final BlueprintValidationCode code;
  private final String location;

  OutputBlueprintValidationException(BlueprintValidationCode code, String location, String detail) {
    super(detail);
    this.code = code;
    this.location = location;
  }

  public BlueprintValidationCode code() {
    return code;
  }

  public String location() {
    return location;
  }
}
