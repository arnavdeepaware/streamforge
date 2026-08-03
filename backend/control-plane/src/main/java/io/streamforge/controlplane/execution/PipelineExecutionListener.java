package io.streamforge.controlplane.execution;

import io.streamforge.pipelineruntime.PipelineReport;
import io.streamforge.pipelineruntime.PipelineRunMetrics;
import io.streamforge.pipelineruntime.deadletter.DeadLetterRecord;

/** Lifecycle callbacks from a backend to the control-plane coordinator. */
public interface PipelineExecutionListener {
  void onRunning();

  void onCompleted(PipelineReport report);

  void onFailed(Throwable failure);

  /** Receives a bounded live counter snapshot without event payloads. */
  default void onMetrics(PipelineRunMetrics metrics) {}

  /** Receives a safe, already-bounded quarantined record. */
  default void onDeadLetter(DeadLetterRecord record) {}
}
