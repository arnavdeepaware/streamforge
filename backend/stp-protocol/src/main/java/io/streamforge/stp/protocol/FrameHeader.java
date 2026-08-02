package io.streamforge.stp.protocol;

import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.SequenceNumber;

/** The common STP v1 frame fields at offsets 0 through 18. */
public record FrameHeader(
    int encodedLength,
    MessageType messageType,
    SequenceNumber sequenceNumber,
    EventTimestamp eventTimestamp) {

  public FrameHeader {
    messageType = StpMessageValidator.require(messageType, "messageType");
    sequenceNumber = StpMessageValidator.require(sequenceNumber, "sequenceNumber");
    eventTimestamp = StpMessageValidator.require(eventTimestamp, "eventTimestamp");
    if (encodedLength != messageType.encodedLength()) {
      throw new InvalidFrameLengthException(encodedLength, messageType);
    }
  }

  /** Returns the complete frame size, including the two-byte length field. */
  public int totalFrameSize() {
    return StpProtocol.totalFrameSize(encodedLength);
  }
}
