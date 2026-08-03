package io.streamforge.controlplane.execution;

/** Persisted lifecycle states for one immutable pipeline revision execution. */
public enum PipelineRunState {
  CREATED,
  VALIDATED,
  STARTING,
  RUNNING,
  STOPPING,
  STOPPED,
  COMPLETED,
  FAILED;

  /** Returns whether this state may transition directly to the requested target. */
  public boolean canTransitionTo(PipelineRunState target) {
    return switch (this) {
      case CREATED -> target == VALIDATED || target == FAILED;
      case VALIDATED -> target == STARTING || target == FAILED;
      case STARTING -> target == RUNNING || target == STOPPING || target == FAILED;
      case RUNNING -> target == STOPPING || target == COMPLETED || target == FAILED;
      case STOPPING -> target == STOPPED || target == FAILED;
      case STOPPED, COMPLETED, FAILED -> false;
    };
  }

  /** Returns whether this state represents an execution that still owns local resources. */
  public boolean active() {
    return this == CREATED
        || this == VALIDATED
        || this == STARTING
        || this == RUNNING
        || this == STOPPING;
  }
}
