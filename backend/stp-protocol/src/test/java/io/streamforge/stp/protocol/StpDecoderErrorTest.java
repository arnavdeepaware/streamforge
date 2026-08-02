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
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StpDecoderErrorTest {

  private final StpDecoder decoder = new StpDecoder();

  @ParameterizedTest
  @MethodSource("truncatedFrames")
  void rejectsInsufficientBytes(byte[] frame, int expectedBytes, int actualBytes) {
    assertThatThrownBy(() -> decoder.decode(frame))
        .isInstanceOfSatisfying(
            TruncatedFrameException.class,
            exception -> {
              assertThat(exception.expectedBytes()).isEqualTo(expectedBytes);
              assertThat(exception.actualBytes()).isEqualTo(actualBytes);
            });
  }

  @Test
  void rejectsBytesAfterOneDeclaredFrame() {
    byte[] frame = Arrays.copyOf(validAddOrderFrame(), StpProtocol.totalFrameSize(47) + 1);

    assertThatThrownBy(() -> decoder.decode(frame)).isInstanceOf(TrailingFrameBytesException.class);
  }

  @ParameterizedTest
  @MethodSource("malformedLengths")
  void rejectsMalformedKnownLengths(byte[] frame) {
    assertThatThrownBy(() -> decoder.decode(frame)).isInstanceOf(InvalidFrameLengthException.class);
  }

  @ParameterizedTest
  @MethodSource("invalidFields")
  void rejectsInvalidKnownFields(byte[] frame, String fieldName) {
    assertThatThrownBy(() -> decoder.decode(frame))
        .isInstanceOfSatisfying(
            InvalidFieldEncodingException.class,
            exception -> assertThat(exception.fieldName()).isEqualTo(fieldName));
  }

  @Test
  void returnsASkippableTypedResultForACompleteUnknownMessage() {
    byte[] frame = {0, 3, 'X', (byte) 0xFF, 0};

    assertThat(decoder.decode(frame)).isEqualTo(new UnknownMessageFrame(3, 'X'));
  }

  private static Stream<Arguments> truncatedFrames() {
    return Stream.of(
        Arguments.of(new byte[0], 2, 0),
        Arguments.of(new byte[] {0}, 2, 1),
        Arguments.of(new byte[] {0, 3, 'X', 1}, 5, 4));
  }

  private static Stream<byte[]> malformedLengths() {
    return Stream.of(new byte[] {0, 0}, new byte[] {0, 1, 'A'});
  }

  private static Stream<Arguments> invalidFields() {
    return Stream.of(
        Arguments.of(mutate(validAddOrderFrame(), 3, (byte) 0x80), "sequence number"),
        Arguments.of(mutate(validAddOrderFrame(), 11, (byte) 0x80), "event timestamp"),
        Arguments.of(mutate(validAddOrderFrame(), 19, (byte) 0x80), "order ID"),
        Arguments.of(mutate(validAddOrderFrame(), 27, (byte) ' '), "symbol"),
        Arguments.of(mutate(validAddOrderFrame(), 35, (byte) 'X'), "side"),
        Arguments.of(zeroRange(validAddOrderFrame(), 36, 40), "quantity"),
        Arguments.of(mutate(validAddOrderFrame(), 48, (byte) 19), "price"));
  }

  private static byte[] validAddOrderFrame() {
    StpMessage message =
        new AddOrderMessage(
            new FrameHeader(
                MessageType.ADD_ORDER.encodedLength(),
                MessageType.ADD_ORDER,
                new SequenceNumber(1),
                new EventTimestamp(1)),
            new OrderId(1),
            new InstrumentSymbol("AAPL"),
            Side.BUY,
            new Quantity(1),
            new FixedDecimal(1, 0));
    return new StpEncoder().encode(message);
  }

  private static byte[] mutate(byte[] frame, int offset, byte value) {
    frame[offset] = value;
    return frame;
  }

  private static byte[] zeroRange(byte[] frame, int start, int end) {
    Arrays.fill(frame, start, end, (byte) 0);
    return frame;
  }
}
