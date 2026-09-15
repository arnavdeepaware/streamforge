package io.streamforge.controlplane.execution;

import io.streamforge.pipelineruntime.PipelineRunMetrics;
import io.streamforge.pipelineruntime.deadletter.DeadLetterRecord;
import java.util.Optional;

/** Lifecycle callbacks from a backend to the control-plane coordinator. */
public interface PipelineExecutionListener {
  void onRunning();

  void onCompleted(PipelineExecutionResult result);

  void onFailed(Throwable failure);

  /** Reports a terminal backend failure together with a capture already published for the run. */
  default void onFailed(Throwable failure, Optional<String> rawCaptureArtifactPath) {
    onFailed(failure);
  }

  /** Receives a bounded live counter snapshot without event payloads. */
  default void onMetrics(PipelineRunMetrics metrics) {}

  /** Receives a safe, already-bounded quarantined record. */
  default void onDeadLetter(DeadLetterRecord record) {}
}
