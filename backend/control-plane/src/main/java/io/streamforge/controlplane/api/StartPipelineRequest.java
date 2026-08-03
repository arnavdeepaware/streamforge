package io.streamforge.controlplane.api;

/** Optional local execution settings for starting the latest immutable pipeline revision. */
public record StartPipelineRequest(DeadLetterOptionsRequest deadLetter) {}
