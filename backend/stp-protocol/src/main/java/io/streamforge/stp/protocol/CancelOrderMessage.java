package io.streamforge.stp.protocol;

import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.Quantity;

/** A fixed-length STP v1 Cancel Order message. */
public record CancelOrderMessage(FrameHeader header, OrderId orderId, Quantity canceledQuantity)
    implements StpMessage {

  public CancelOrderMessage {
    header = StpMessageValidator.requireHeaderType(header, MessageType.CANCEL_ORDER);
    orderId = StpMessageValidator.require(orderId, "orderId");
    canceledQuantity = StpMessageValidator.require(canceledQuantity, "canceledQuantity");
  }
}
