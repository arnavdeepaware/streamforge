package io.streamforge.stp.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StpProtocolTest {

  @Test
  void matchesTheDocumentedCommonHeaderOffsets() {
    assertThat(StpProtocol.LENGTH_FIELD_OFFSET).isZero();
    assertThat(StpProtocol.LENGTH_FIELD_WIDTH).isEqualTo(2);
    assertThat(StpProtocol.MESSAGE_TYPE_OFFSET).isEqualTo(2);
    assertThat(StpProtocol.SEQUENCE_NUMBER_OFFSET).isEqualTo(3);
    assertThat(StpProtocol.EVENT_TIMESTAMP_OFFSET).isEqualTo(11);
    assertThat(StpProtocol.COMMON_HEADER_SIZE).isEqualTo(19);
  }

  @Test
  void derivesTheDocumentedMessageSizes() {
    assertThat(StpProtocol.ADD_ORDER_ENCODED_LENGTH).isEqualTo(47);
    assertThat(StpProtocol.EXECUTE_ORDER_ENCODED_LENGTH).isEqualTo(29);
    assertThat(StpProtocol.CANCEL_ORDER_ENCODED_LENGTH).isEqualTo(29);
    assertThat(StpProtocol.TRADE_ENCODED_LENGTH).isEqualTo(47);
    assertThat(StpProtocol.totalFrameSize(StpProtocol.ADD_ORDER_ENCODED_LENGTH)).isEqualTo(49);
    assertThat(StpProtocol.totalFrameSize(StpProtocol.EXECUTE_ORDER_ENCODED_LENGTH)).isEqualTo(31);
  }

  @Test
  void rejectsLengthsOutsideTheUnsignedSixteenBitFrameDomain() {
    assertThatThrownBy(() -> StpProtocol.totalFrameSize(0))
        .isInstanceOf(InvalidFrameLengthException.class);
    assertThatThrownBy(() -> StpProtocol.totalFrameSize(StpProtocol.MAX_ENCODED_LENGTH + 1))
        .isInstanceOf(InvalidFrameLengthException.class);
  }
}
