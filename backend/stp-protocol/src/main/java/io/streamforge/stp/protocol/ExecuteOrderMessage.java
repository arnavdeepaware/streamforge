package io.streamforge.stp.protocol;

import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.Quantity;

/** A fixed-length STP v1 Execute Order message. */
public record ExecuteOrderMessage(FrameHeader header, OrderId orderId, Quantity executedQuantity)
    implements StpMessage {

  public ExecuteOrderMessage {
    header = StpMessageValidator.requireHeaderType(header, MessageType.EXECUTE_ORDER);
    orderId = StpMessageValidator.require(orderId, "orderId");
    executedQuantity = StpMessageValidator.require(executedQuantity, "executedQuantity");
  }
}
