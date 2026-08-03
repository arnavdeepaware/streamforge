package io.streamforge.pipelineruntime.deadletter;

import java.nio.file.Path;
import java.util.Optional;

/** Local dead-letter policy and bounded payload-retention settings. */
public record DeadLetterConfig(
    DeadLetterPolicy policy, Optional<Path> path, boolean includePayload, int maximumPayloadBytes) {
  public static final int DEFAULT_MAXIMUM_PAYLOAD_BYTES = 4_096;

  public DeadLetterConfig {
    if (policy == null || path == null || maximumPayloadBytes < 0) {
      throw new IllegalArgumentException("dead-letter configuration fields are invalid");
    }
    if (policy == DeadLetterPolicy.QUARANTINE && path.isEmpty()) {
      throw new IllegalArgumentException("quarantine policy requires a dead-letter path");
    }
  }

  /** Uses a bounded JSONL quarantine file. */
  public static DeadLetterConfig quarantine(
      Path path, boolean includePayload, int maximumPayloadBytes) {
    return new DeadLetterConfig(
        DeadLetterPolicy.QUARANTINE, Optional.of(path), includePayload, maximumPayloadBytes);
  }

  /** Continues without a durable record. Useful only for explicitly disposable input. */
  public static DeadLetterConfig skip() {
    return new DeadLetterConfig(DeadLetterPolicy.SKIP, Optional.empty(), false, 0);
  }

  /** Stops on the first record-level failure without a durable record. */
  public static DeadLetterConfig failFast() {
    return new DeadLetterConfig(DeadLetterPolicy.FAIL_FAST, Optional.empty(), false, 0);
  }
}
