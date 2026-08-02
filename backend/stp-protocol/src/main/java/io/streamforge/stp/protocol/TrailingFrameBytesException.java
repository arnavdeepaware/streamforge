package io.streamforge.stp.protocol;

/** Raised when one-frame decoding input contains bytes after the declared frame. */
public final class TrailingFrameBytesException extends StpDecodingException {

  private final int declaredFrameSize;
  private final int actualBytes;

  TrailingFrameBytesException(int declaredFrameSize, int actualBytes) {
    super("STP frame declares " + declaredFrameSize + " bytes but input contains " + actualBytes);
    this.declaredFrameSize = declaredFrameSize;
    this.actualBytes = actualBytes;
  }

  public int declaredFrameSize() {
    return declaredFrameSize;
  }

  public int actualBytes() {
    return actualBytes;
  }
}
