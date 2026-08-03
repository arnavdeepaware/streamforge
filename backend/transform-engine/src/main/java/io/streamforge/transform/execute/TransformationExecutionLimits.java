package io.streamforge.transform.execute;

/** Bounded resource policy applied to each deterministic transformation execution. */
public record TransformationExecutionLimits(
    int maxNestingDepth, int maxOperationCount, int maxOutputFieldCount) {

  public static final TransformationExecutionLimits DEFAULT =
      new TransformationExecutionLimits(16, 256, 512);

  public TransformationExecutionLimits {
    if (maxNestingDepth < 1 || maxOperationCount < 1 || maxOutputFieldCount < 1) {
      throw new IllegalArgumentException("transformation execution limits must be positive");
    }
  }
}
