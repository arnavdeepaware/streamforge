package io.streamforge.stp.protocol;

/** Raised when a declared frame exceeds an incremental decoder's configured bound. */
public final class FrameTooLargeException extends StpDecodingException {

  private final int declaredFrameSize;
  private final int maximumFrameSize;

  FrameTooLargeException(int declaredFrameSize, int maximumFrameSize) {
    super(
        "STP frame declares "
            + declaredFrameSize
            + " bytes but configured maximum is "
            + maximumFrameSize);
    this.declaredFrameSize = declaredFrameSize;
    this.maximumFrameSize = maximumFrameSize;
  }

  public int declaredFrameSize() {
    return declaredFrameSize;
  }

  public int maximumFrameSize() {
    return maximumFrameSize;
  }
}
