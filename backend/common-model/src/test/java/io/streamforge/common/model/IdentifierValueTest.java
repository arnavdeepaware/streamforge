package io.streamforge.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.LongStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IdentifierValueTest {

  @ParameterizedTest
  @MethodSource("validIds")
  void acceptsNonnegativeOrderAndTradeIds(long value) {
    assertThat(new OrderId(value).value()).isEqualTo(value);
    assertThat(new TradeId(value).value()).isEqualTo(value);
    assertThat(new OrderId(value).toString()).isEqualTo(Long.toString(value));
    assertThat(new TradeId(value)).isEqualTo(new TradeId(value));
  }

  @ParameterizedTest
  @MethodSource("invalidIds")
  void rejectsNegativeOrderAndTradeIds(long value) {
    assertThatIllegalArgumentException().isThrownBy(() -> new OrderId(value));
    assertThatIllegalArgumentException().isThrownBy(() -> new TradeId(value));
  }

  @ParameterizedTest
  @MethodSource("validSequences")
  void acceptsPositiveSequenceNumbers(long value) {
    assertThat(new SequenceNumber(value).value()).isEqualTo(value);
    assertThat(new SequenceNumber(value).toString()).isEqualTo(Long.toString(value));
  }

  @ParameterizedTest
  @MethodSource("invalidSequences")
  void rejectsNonpositiveSequenceNumbers(long value) {
    assertThatIllegalArgumentException().isThrownBy(() -> new SequenceNumber(value));
  }

  private static LongStream validIds() {
    return LongStream.of(0L, 1L, Long.MAX_VALUE);
  }

  private static LongStream invalidIds() {
    return LongStream.of(-1L, Long.MIN_VALUE);
  }

  private static LongStream validSequences() {
    return LongStream.of(1L, Long.MAX_VALUE);
  }

  private static LongStream invalidSequences() {
    return LongStream.of(Long.MIN_VALUE, -1L, 0L);
  }
}
