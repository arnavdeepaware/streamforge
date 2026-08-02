package io.streamforge.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FixedDecimalTest {

  @ParameterizedTest
  @MethodSource("validDecimals")
  void preservesExactMantissaAndScale(long mantissa, int scale, String displayValue) {
    FixedDecimal value = new FixedDecimal(mantissa, scale);

    assertThat(value.mantissa()).isEqualTo(mantissa);
    assertThat(value.scale()).isEqualTo(scale);
    assertThat(value.toBigDecimal()).isEqualByComparingTo(new BigDecimal(displayValue));
    assertThat(value.toString()).isEqualTo(displayValue);
    assertThat(value).isEqualTo(new FixedDecimal(mantissa, scale));
  }

  @ParameterizedTest
  @MethodSource("invalidScales")
  void rejectsUnsupportedScales(int scale) {
    assertThatIllegalArgumentException().isThrownBy(() -> new FixedDecimal(0, scale));
  }

  private static Stream<Arguments> validDecimals() {
    return Stream.of(
        Arguments.of(0L, 0, "0"),
        Arguments.of(12_345L, 2, "123.45"),
        Arguments.of(-120L, 2, "-1.20"),
        Arguments.of(Long.MIN_VALUE, 18, "-9.223372036854775808"));
  }

  private static Stream<Integer> invalidScales() {
    return Stream.of(Integer.MIN_VALUE, -1, FixedDecimal.MAX_SCALE + 1, Integer.MAX_VALUE);
  }
}
