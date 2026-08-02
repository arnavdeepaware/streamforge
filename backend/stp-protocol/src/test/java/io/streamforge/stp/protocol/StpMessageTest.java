package io.streamforge.stp.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.Quantity;
import io.streamforge.common.model.SequenceNumber;
import io.streamforge.common.model.Side;
import io.streamforge.common.model.TradeId;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StpMessageTest {

  @ParameterizedTest
  @MethodSource("validMessages")
  void exposesTheDocumentedHeaderAndFrameSize(
      StpMessage message, MessageType messageType, int encodedLength, int totalFrameSize) {
    assertThat(message.messageType()).isEqualTo(messageType);
    assertThat(message.encodedLength()).isEqualTo(encodedLength);
    assertThat(message.totalFrameSize()).isEqualTo(totalFrameSize);
  }

  @ParameterizedTest
  @MethodSource("wrongHeaderMessages")
  void rejectsMessageHeadersForOtherMessageTypes(MessageFactory factory, FrameHeader header) {
    assertThatThrownBy(() -> factory.create(header))
        .isInstanceOf(MessageHeaderMismatchException.class);
  }

  @ParameterizedTest
  @MethodSource("missingFieldMessages")
  void rejectsMissingMessageFields(MessageFactory factory, FrameHeader header) {
    assertThatThrownBy(() -> factory.create(header)).isInstanceOf(StpValidationException.class);
  }

  private static Stream<Arguments> validMessages() {
    return Stream.of(
        Arguments.of(
            new AddOrderMessage(
                header(MessageType.ADD_ORDER, 1, 1_000_000_000L),
                new OrderId(1001),
                new InstrumentSymbol("AAPL"),
                Side.BUY,
                new Quantity(100),
                new FixedDecimal(12_345, 2)),
            MessageType.ADD_ORDER,
            47,
            49),
        Arguments.of(
            new ExecuteOrderMessage(
                header(MessageType.EXECUTE_ORDER, 2, 1_000_000_100L),
                new OrderId(1001),
                new Quantity(40)),
            MessageType.EXECUTE_ORDER,
            29,
            31),
        Arguments.of(
            new CancelOrderMessage(
                header(MessageType.CANCEL_ORDER, 3, 1_000_000_200L),
                new OrderId(1001),
                new Quantity(60)),
            MessageType.CANCEL_ORDER,
            29,
            31),
        Arguments.of(
            new TradeMessage(
                header(MessageType.TRADE, 4, 1_000_000_300L),
                new TradeId(5001),
                new InstrumentSymbol("MSFT"),
                Side.SELL,
                new Quantity(25),
                new FixedDecimal(25_005, 2)),
            MessageType.TRADE,
            47,
            49));
  }

  private static Stream<Arguments> wrongHeaderMessages() {
    return Stream.of(
        Arguments.of(
            (MessageFactory)
                header ->
                    new AddOrderMessage(
                        header,
                        new OrderId(1),
                        new InstrumentSymbol("AAPL"),
                        Side.BUY,
                        new Quantity(1),
                        new FixedDecimal(1, 0)),
            header(MessageType.TRADE, 1, 1)),
        Arguments.of(
            (MessageFactory)
                header -> new ExecuteOrderMessage(header, new OrderId(1), new Quantity(1)),
            header(MessageType.CANCEL_ORDER, 1, 1)),
        Arguments.of(
            (MessageFactory)
                header -> new CancelOrderMessage(header, new OrderId(1), new Quantity(1)),
            header(MessageType.EXECUTE_ORDER, 1, 1)),
        Arguments.of(
            (MessageFactory)
                header ->
                    new TradeMessage(
                        header,
                        new TradeId(1),
                        new InstrumentSymbol("MSFT"),
                        Side.SELL,
                        new Quantity(1),
                        new FixedDecimal(1, 0)),
            header(MessageType.ADD_ORDER, 1, 1)));
  }

  private static Stream<Arguments> missingFieldMessages() {
    return Stream.of(
        Arguments.of(
            (MessageFactory)
                header ->
                    new AddOrderMessage(
                        header,
                        null,
                        new InstrumentSymbol("AAPL"),
                        Side.BUY,
                        new Quantity(1),
                        new FixedDecimal(1, 0)),
            header(MessageType.ADD_ORDER, 1, 1)),
        Arguments.of(
            (MessageFactory) header -> new ExecuteOrderMessage(header, new OrderId(1), null),
            header(MessageType.EXECUTE_ORDER, 1, 1)),
        Arguments.of(
            (MessageFactory) header -> new CancelOrderMessage(header, null, new Quantity(1)),
            header(MessageType.CANCEL_ORDER, 1, 1)),
        Arguments.of(
            (MessageFactory)
                header ->
                    new TradeMessage(
                        header,
                        new TradeId(1),
                        new InstrumentSymbol("MSFT"),
                        null,
                        new Quantity(1),
                        new FixedDecimal(1, 0)),
            header(MessageType.TRADE, 1, 1)));
  }

  private static FrameHeader header(MessageType type, long sequence, long timestamp) {
    return new FrameHeader(
        type.encodedLength(), type, new SequenceNumber(sequence), new EventTimestamp(timestamp));
  }

  @FunctionalInterface
  private interface MessageFactory {
    StpMessage create(FrameHeader header);
  }
}
