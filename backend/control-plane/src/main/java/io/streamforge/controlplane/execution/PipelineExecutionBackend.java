package io.streamforge.controlplane.execution;

/** Boundary that permits the control plane to switch from local runs to remote workers later. */
public interface PipelineExecutionBackend {
  PipelineExecutionHandle start(
      PipelineExecutionCommand command, PipelineExecutionListener listener);
}
