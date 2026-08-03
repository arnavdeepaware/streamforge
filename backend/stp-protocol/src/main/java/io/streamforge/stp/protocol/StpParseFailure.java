package io.streamforge.stp.protocol;

/** A rejected frame or incomplete stream, including whether parsing can safely continue. */
public record StpParseFailure(StpProtocolException error, boolean recoverable)
    implements StpParseEvent {

  public StpParseFailure {
    error = StpMessageValidator.require(error, "error");
  }
}
