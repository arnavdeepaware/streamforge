package io.streamforge.stp.protocol;

/** Raised when a known message does not increase the accepted stream sequence. */
public final class InvalidStreamSequenceException extends StpDecodingException {

  private final long previousSequence;
  private final long currentSequence;

  InvalidStreamSequenceException(long previousSequence, long currentSequence) {
    super(
        "STP sequence must increase: previous="
            + previousSequence
            + ", current="
            + currentSequence);
    this.previousSequence = previousSequence;
    this.currentSequence = currentSequence;
  }

  public long previousSequence() {
    return previousSequence;
  }

  public long currentSequence() {
    return currentSequence;
  }
}
