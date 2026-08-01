package io.streamforge.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InstrumentSymbolTest {

  @ParameterizedTest
  @MethodSource("validSymbols")
  void normalizesStpTrailingPadding(String input, String expected) {
    InstrumentSymbol symbol = new InstrumentSymbol(input);

    assertThat(symbol.value()).isEqualTo(expected);
    assertThat(symbol.toStpPaddedAscii())
        .isEqualTo(expected + " ".repeat(InstrumentSymbol.STP_FIELD_LENGTH - expected.length()));
    assertThat(symbol.toString()).isEqualTo(expected);
    assertThat(symbol).isEqualTo(new InstrumentSymbol(expected));
  }

  @ParameterizedTest
  @MethodSource("invalidSymbols")
  void rejectsInvalidStpSymbolValues(String value) {
    assertThatIllegalArgumentException().isThrownBy(() -> new InstrumentSymbol(value));
  }

  @ParameterizedTest
  @MethodSource("validStpFields")
  void decodesExactStpFields(String field, String expected) {
    assertThat(InstrumentSymbol.fromStpField(field.getBytes(StandardCharsets.US_ASCII)).value())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("invalidStpFields")
  void rejectsMalformedStpFields(byte[] field) {
    assertThatIllegalArgumentException().isThrownBy(() -> InstrumentSymbol.fromStpField(field));
  }

  private static Stream<Arguments> validSymbols() {
    return Stream.of(
        Arguments.of("A", "A"),
        Arguments.of("AAPL    ", "AAPL"),
        Arguments.of("ABCDEFGH", "ABCDEFGH"));
  }

  private static Stream<String> invalidSymbols() {
    return Stream.of(
        null, "", "        ", " AAPL", "AA PL", "AAPL     ", "AAPL\t", "AAPL\u0000", "AAPL\u00C5");
  }

  private static Stream<Arguments> validStpFields() {
    return Stream.of(Arguments.of("MSFT    ", "MSFT"), Arguments.of("ABCDEFGH", "ABCDEFGH"));
  }

  private static Stream<byte[]> invalidStpFields() {
    return Stream.of(
        "AAPL".getBytes(StandardCharsets.US_ASCII),
        new byte[] {'A', 'A', ' ', 'P', 'L', ' ', ' ', ' '},
        new byte[] {'A', 'A', 'P', 'L', ' ', ' ', ' ', (byte) 0x80});
  }
}
