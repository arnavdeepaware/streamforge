package io.streamforge.transform.blueprint;

/** Maximum static and rendered size of a nested output blueprint. */
public record OutputBlueprintLimits(int maxDepth, int maxFieldCount) {
  public static final OutputBlueprintLimits DEFAULT = new OutputBlueprintLimits(16, 512);

  public OutputBlueprintLimits {
    if (maxDepth < 1 || maxFieldCount < 1)
      throw new IllegalArgumentException("blueprint limits must be positive");
  }
}
