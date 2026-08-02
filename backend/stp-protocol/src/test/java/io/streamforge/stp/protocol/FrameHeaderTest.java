package io.streamforge.stp.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.SequenceNumber;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FrameHeaderTest {

  @ParameterizedTest
  @MethodSource("validHeaders")
  void preservesKnownFrameHeaderValues(
      MessageType messageType, int encodedLength, int totalFrameSize) {
    FrameHeader header =
        new FrameHeader(
            encodedLength, messageType, new SequenceNumber(1), new EventTimestamp(1_000_000_000L));

    assertThat(header.messageType()).isEqualTo(messageType);
    assertThat(header.encodedLength()).isEqualTo(encodedLength);
    assertThat(header.totalFrameSize()).isEqualTo(totalFrameSize);
  }

  @ParameterizedTest
  @MethodSource("invalidLengths")
  void rejectsLengthsThatDoNotMatchKnownMessageLayouts(MessageType messageType, int length) {
    assertThatThrownBy(
            () ->
                new FrameHeader(
                    length, messageType, new SequenceNumber(1), new EventTimestamp(1_000_000_000L)))
        .isInstanceOf(InvalidFrameLengthException.class);
  }

  @ParameterizedTest
  @MethodSource("nullComponents")
  void rejectsMissingHeaderComponents(
      MessageType messageType, SequenceNumber sequenceNumber, EventTimestamp eventTimestamp) {
    assertThatThrownBy(
            () ->
                new FrameHeader(
                    StpProtocol.ADD_ORDER_ENCODED_LENGTH,
                    messageType,
                    sequenceNumber,
                    eventTimestamp))
        .isInstanceOf(StpValidationException.class);
  }

  private static Stream<Arguments> validHeaders() {
    return Stream.of(
        Arguments.of(MessageType.ADD_ORDER, 47, 49),
        Arguments.of(MessageType.EXECUTE_ORDER, 29, 31),
        Arguments.of(MessageType.CANCEL_ORDER, 29, 31),
        Arguments.of(MessageType.TRADE, 47, 49));
  }

  private static Stream<Arguments> invalidLengths() {
    return Stream.of(
        Arguments.of(MessageType.ADD_ORDER, 46),
        Arguments.of(MessageType.EXECUTE_ORDER, 0),
        Arguments.of(MessageType.CANCEL_ORDER, 30),
        Arguments.of(MessageType.TRADE, StpProtocol.MAX_ENCODED_LENGTH));
  }

  private static Stream<Arguments> nullComponents() {
    return Stream.of(
        Arguments.of(null, new SequenceNumber(1), new EventTimestamp(1)),
        Arguments.of(MessageType.ADD_ORDER, null, new EventTimestamp(1)),
        Arguments.of(MessageType.ADD_ORDER, new SequenceNumber(1), null));
  }
}
