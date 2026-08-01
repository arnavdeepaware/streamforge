package io.streamforge.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SideTest {

  @ParameterizedTest
  @MethodSource("validCodes")
  void mapsStpCodesToSides(char code, Side expected) {
    assertThat(Side.fromStpCode(code)).isEqualTo(expected);
    assertThat(expected.stpCode()).isEqualTo(code);
    assertThat(expected.toString()).isEqualTo(Character.toString(code));
  }

  @ParameterizedTest
  @MethodSource("invalidCodes")
  void rejectsUnknownStpCodes(char code) {
    assertThatIllegalArgumentException().isThrownBy(() -> Side.fromStpCode(code));
  }

  private static Stream<Arguments> validCodes() {
    return Stream.of(Arguments.of('B', Side.BUY), Arguments.of('S', Side.SELL));
  }

  private static Stream<Character> invalidCodes() {
    return Stream.of('b', 's', 'X', '\u0000');
  }
}
