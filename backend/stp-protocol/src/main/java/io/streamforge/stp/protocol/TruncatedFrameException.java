package io.streamforge.stp.protocol;

/** Raised when input ends before the declared STP frame is complete. */
public final class TruncatedFrameException extends StpDecodingException {

  private final int expectedBytes;
  private final int actualBytes;

  TruncatedFrameException(int expectedBytes, int actualBytes) {
    super(
        "STP frame requires "
            + expectedBytes
            + " bytes but only "
            + actualBytes
            + " are available");
    this.expectedBytes = expectedBytes;
    this.actualBytes = actualBytes;
  }

  public int expectedBytes() {
    return expectedBytes;
  }

  public int actualBytes() {
    return actualBytes;
  }
}
