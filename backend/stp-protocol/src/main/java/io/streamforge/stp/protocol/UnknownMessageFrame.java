package io.streamforge.stp.protocol;

/** A complete, skippable frame whose message type is not assigned by STP v1. */
public record UnknownMessageFrame(int encodedLength, int messageTypeCode)
    implements StpDecodeResult {

  public UnknownMessageFrame {
    StpProtocol.totalFrameSize(encodedLength);
    if (messageTypeCode < 0 || messageTypeCode > 0xFF) {
      throw new StpValidationException("message type code must fit in one unsigned byte");
    }
  }

  public int totalFrameSize() {
    return StpProtocol.totalFrameSize(encodedLength);
  }
}
