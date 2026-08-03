package io.streamforge.controlplane.api;

import java.time.Instant;

/** Safe, bounded dead-letter representation returned by local run monitoring APIs. */
public record DeadLetterResponse(
    String failureId,
    String stage,
    String category,
    String sourceLocation,
    String safeMessage,
    String retryability,
    Instant timestamp,
    String payloadEncoding,
    String payloadPreview,
    boolean payloadTruncated) {}
