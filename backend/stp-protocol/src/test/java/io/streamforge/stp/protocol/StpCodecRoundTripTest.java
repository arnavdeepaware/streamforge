package io.streamforge.stp.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.Quantity;
import io.streamforge.common.model.SequenceNumber;
import io.streamforge.common.model.Side;
import io.streamforge.common.model.TradeId;
import java.nio.ByteBuffer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class StpCodecRoundTripTest {

  private final StpEncoder encoder = new StpEncoder();
  private final StpDecoder decoder = new StpDecoder();

  @ParameterizedTest
  @MethodSource("messages")
  void roundTripsEveryMessageWithoutChangingExactValues(StpMessage message) {
    byte[] encoded = encoder.encode(message);

    assertThat(decoder.decode(encoded)).isEqualTo(message);
    assertThat(decoder.decode(ByteBuffer.wrap(encoded))).isEqualTo(message);
  }

  private static Stream<StpMessage> messages() {
    return Stream.of(
        new AddOrderMessage(
            header(MessageType.ADD_ORDER, Long.MAX_VALUE, Long.MAX_VALUE),
            new OrderId(Long.MAX_VALUE),
            new InstrumentSymbol("ABCDEFGH"),
            Side.SELL,
            new Quantity(Quantity.MAX_VALUE),
            new FixedDecimal(Long.MIN_VALUE, FixedDecimal.MAX_SCALE)),
        new ExecuteOrderMessage(
            header(MessageType.EXECUTE_ORDER, 1, 0),
            new OrderId(0),
            new Quantity(Quantity.MAX_VALUE)),
        new CancelOrderMessage(
            header(MessageType.CANCEL_ORDER, 2, 1), new OrderId(1), new Quantity(1)),
        new TradeMessage(
            header(MessageType.TRADE, 3, 2),
            new TradeId(Long.MAX_VALUE),
            new InstrumentSymbol("T"),
            Side.BUY,
            new Quantity(1),
            new FixedDecimal(Long.MAX_VALUE, 0)));
  }

  private static FrameHeader header(MessageType type, long sequence, long timestamp) {
    return new FrameHeader(
        type.encodedLength(), type, new SequenceNumber(sequence), new EventTimestamp(timestamp));
  }
}
