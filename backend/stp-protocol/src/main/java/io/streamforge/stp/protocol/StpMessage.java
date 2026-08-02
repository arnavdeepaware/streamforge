package io.streamforge.stp.protocol;

/** A validated known STP v1 message. */
public sealed interface StpMessage
    permits AddOrderMessage, ExecuteOrderMessage, CancelOrderMessage, TradeMessage {

  FrameHeader header();

  default MessageType messageType() {
    return header().messageType();
  }

  default int encodedLength() {
    return header().encodedLength();
  }

  default int totalFrameSize() {
    return header().totalFrameSize();
  }
}
