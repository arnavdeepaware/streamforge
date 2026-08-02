package io.streamforge.stp.protocol;

/** An ordered result produced while incrementally parsing an STP byte stream. */
public sealed interface StpParseEvent permits ParsedStpFrame, StpParseFailure {}
