package io.streamforge.controlplane.execution;

import io.streamforge.pipelineruntime.deadletter.DeadLetterPolicy;

/** Typed record-level failure policy supplied to one managed local execution. */
public record LocalDeadLetterOptions(
    DeadLetterPolicy policy, boolean includePayload, int maximumPayloadBytes) {
  public static final int MAXIMUM_PAYLOAD_BYTES = 1_048_576;

  public LocalDeadLetterOptions {
    if (policy == null || maximumPayloadBytes < 0 || maximumPayloadBytes > MAXIMUM_PAYLOAD_BYTES) {
      throw new IllegalArgumentException("dead-letter options are invalid");
    }
    if (policy != DeadLetterPolicy.QUARANTINE && includePayload) {
      throw new IllegalArgumentException("payload capture requires quarantine policy");
    }
  }
}
