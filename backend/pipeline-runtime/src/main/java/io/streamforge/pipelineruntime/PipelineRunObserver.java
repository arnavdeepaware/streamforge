package io.streamforge.pipelineruntime;

import io.streamforge.pipelineruntime.deadletter.DeadLetterRecord;

/** Receives synchronous snapshots from one local run without affecting execution semantics. */
public interface PipelineRunObserver {
  PipelineRunObserver NO_OP =
      new PipelineRunObserver() {
        @Override
        public void onMetrics(PipelineRunMetrics metrics) {}

        @Override
        public void onDeadLetter(DeadLetterRecord record) {}
      };

  /** Receives a nonnegative counter snapshot after processing progress. */
  void onMetrics(PipelineRunMetrics metrics);

  /** Receives each locally quarantined dead-letter record after it is durably staged. */
  void onDeadLetter(DeadLetterRecord record);
}
