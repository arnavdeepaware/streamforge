package io.streamforge.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.LongStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class QuantityTest {

  @ParameterizedTest
  @MethodSource("validQuantities")
  void acceptsTheCompleteUint32Domain(long value) {
    Quantity quantity = new Quantity(value);

    assertThat(quantity.value()).isEqualTo(value);
    assertThat(quantity.toString()).isEqualTo(Long.toString(value));
    assertThat(quantity).isEqualTo(new Quantity(value));
  }

  @ParameterizedTest
  @MethodSource("invalidQuantities")
  void rejectsValuesOutsideTheUint32Domain(long value) {
    assertThatIllegalArgumentException().isThrownBy(() -> new Quantity(value));
  }

  private static LongStream validQuantities() {
    return LongStream.of(1L, 100L, Quantity.MAX_VALUE);
  }

  private static LongStream invalidQuantities() {
    return LongStream.of(Long.MIN_VALUE, -1L, 0L, Quantity.MAX_VALUE + 1, Long.MAX_VALUE);
  }
}
