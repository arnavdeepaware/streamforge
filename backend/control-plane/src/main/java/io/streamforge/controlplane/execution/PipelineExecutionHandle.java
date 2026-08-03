package io.streamforge.controlplane.execution;

/** Cancellation handle for a locally hosted execution; future remote workers can implement it. */
public interface PipelineExecutionHandle {
  void cancel();
}
