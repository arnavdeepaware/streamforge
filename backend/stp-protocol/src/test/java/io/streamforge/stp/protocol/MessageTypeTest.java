package io.streamforge.stp.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessageTypeTest {

  @ParameterizedTest
  @MethodSource("knownTypes")
  void resolvesEveryDocumentedWireCode(MessageType expected, char wireCode, int encodedLength) {
    assertThat(MessageType.fromWireCode(wireCode)).isEqualTo(expected);
    assertThat(expected.wireCode()).isEqualTo(wireCode);
    assertThat(expected.encodedLength()).isEqualTo(encodedLength);
  }

  @ParameterizedTest
  @MethodSource("unknownCodes")
  void rejectsUnknownWireCodes(char wireCode) {
    assertThatThrownBy(() -> MessageType.fromWireCode(wireCode))
        .isInstanceOf(UnknownMessageTypeException.class);
  }

  private static Stream<Arguments> knownTypes() {
    return Stream.of(
        Arguments.of(MessageType.ADD_ORDER, 'A', 47),
        Arguments.of(MessageType.EXECUTE_ORDER, 'E', 29),
        Arguments.of(MessageType.CANCEL_ORDER, 'C', 29),
        Arguments.of(MessageType.TRADE, 'T', 47));
  }

  private static Stream<Character> unknownCodes() {
    return Stream.of('a', 'X', '\u0000');
  }
}
