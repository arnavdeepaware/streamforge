package io.streamforge.stp.protocol;

/** Result of decoding one complete STP frame. */
public sealed interface StpDecodeResult permits StpMessage, UnknownMessageFrame {}
