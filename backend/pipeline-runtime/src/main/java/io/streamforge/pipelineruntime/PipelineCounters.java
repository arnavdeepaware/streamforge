package io.streamforge.pipelineruntime;

/** Final nonnegative counts from one local pipeline run. */
public record PipelineCounters(
    long received, long parsed, long normalized, long filtered, long emitted, long failed) {
  public PipelineCounters {
    if (received < 0 || parsed < 0 || normalized < 0 || filtered < 0 || emitted < 0 || failed < 0) {
      throw new IllegalArgumentException("pipeline counters must not be negative");
    }
  }
}
