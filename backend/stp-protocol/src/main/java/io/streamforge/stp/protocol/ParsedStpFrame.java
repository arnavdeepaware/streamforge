package io.streamforge.stp.protocol;

/** A successfully parsed known or unknown STP frame. */
public record ParsedStpFrame(StpDecodeResult result) implements StpParseEvent {

  public ParsedStpFrame {
    result = StpMessageValidator.require(result, "result");
  }
}
