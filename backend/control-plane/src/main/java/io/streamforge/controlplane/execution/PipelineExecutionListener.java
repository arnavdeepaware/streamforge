package io.streamforge.controlplane.execution;

import io.streamforge.pipelineruntime.PipelineReport;

/** Lifecycle callbacks from a backend to the control-plane coordinator. */
public interface PipelineExecutionListener {
  void onRunning();

  void onCompleted(PipelineReport report);

  void onFailed(Throwable failure);
}
