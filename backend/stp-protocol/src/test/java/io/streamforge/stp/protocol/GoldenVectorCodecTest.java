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
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Tests the fixtures published in schemas/examples/stp-v1-golden-vectors.md. */
class GoldenVectorCodecTest {

  private final StpEncoder encoder = new StpEncoder();
  private final StpDecoder decoder = new StpDecoder();

  @ParameterizedTest
  @MethodSource("goldenVectors")
  void encodesTheDocumentedBytesExactly(StpMessage message, String hexadecimalFrame) {
    assertThat(encoder.encode(message)).containsExactly(hexadecimalFrame(hexadecimalFrame));
  }

  @ParameterizedTest
  @MethodSource("goldenVectors")
  void decodesTheDocumentedBytesExactly(StpMessage message, String hexadecimalFrame) {
    assertThat(decoder.decode(hexadecimalFrame(hexadecimalFrame))).isEqualTo(message);
  }

  private static Stream<Arguments> goldenVectors() {
    return Stream.of(
        Arguments.of(
            new AddOrderMessage(
                header(MessageType.ADD_ORDER, 1, 1_000_000_000L),
                new OrderId(1001),
                new InstrumentSymbol("AAPL"),
                Side.BUY,
                new Quantity(100),
                new FixedDecimal(12_345, 2)),
            "00 2F 41 00 00 00 00 00 00 00 01 00 00 00 00 3B 9A CA 00 00 00 00 00 00 00 03 E9 41 41 50 4C 20 20 20 20 42 00 00 00 64 00 00 00 00 00 00 30 39 02"),
        Arguments.of(
            new ExecuteOrderMessage(
                header(MessageType.EXECUTE_ORDER, 2, 1_000_000_100L),
                new OrderId(1001),
                new Quantity(40)),
            "00 1D 45 00 00 00 00 00 00 00 02 00 00 00 00 3B 9A CA 64 00 00 00 00 00 00 03 E9 00 00 00 28"),
        Arguments.of(
            new CancelOrderMessage(
                header(MessageType.CANCEL_ORDER, 3, 1_000_000_200L),
                new OrderId(1001),
                new Quantity(60)),
            "00 1D 43 00 00 00 00 00 00 00 03 00 00 00 00 3B 9A CA C8 00 00 00 00 00 00 03 E9 00 00 00 3C"),
        Arguments.of(
            new TradeMessage(
                header(MessageType.TRADE, 4, 1_000_000_300L),
                new TradeId(5001),
                new InstrumentSymbol("MSFT"),
                Side.SELL,
                new Quantity(25),
                new FixedDecimal(25_005, 2)),
            "00 2F 54 00 00 00 00 00 00 00 04 00 00 00 00 3B 9A CB 2C 00 00 00 00 00 00 13 89 4D 53 46 54 20 20 20 20 53 00 00 00 19 00 00 00 00 00 00 61 AD 02"));
  }

  private static FrameHeader header(MessageType type, long sequence, long timestamp) {
    return new FrameHeader(
        type.encodedLength(), type, new SequenceNumber(sequence), new EventTimestamp(timestamp));
  }

  private static byte[] hexadecimalFrame(String value) {
    return HexFormat.of().parseHex(value.replace(" ", ""));
  }
}
