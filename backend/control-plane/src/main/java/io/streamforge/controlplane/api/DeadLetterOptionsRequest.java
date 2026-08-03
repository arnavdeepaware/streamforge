package io.streamforge.controlplane.api;

import io.streamforge.pipelineruntime.deadletter.DeadLetterPolicy;

/** Typed record-level failure settings; storage paths remain server-owned. */
public record DeadLetterOptionsRequest(
    DeadLetterPolicy policy, boolean includePayload, int maximumPayloadBytes) {}
