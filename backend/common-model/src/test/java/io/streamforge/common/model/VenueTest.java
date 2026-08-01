package io.streamforge.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VenueTest {

  @ParameterizedTest
  @MethodSource("validVenues")
  void normalizesIdentifiersToUppercaseAscii(String input, String expected) {
    Venue venue = new Venue(input);

    assertThat(venue.value()).isEqualTo(expected);
    assertThat(venue.toString()).isEqualTo(expected);
    assertThat(venue).isEqualTo(new Venue(expected));
  }

  @ParameterizedTest
  @MethodSource("invalidVenues")
  void rejectsInvalidVenueIdentifiers(String value) {
    assertThatIllegalArgumentException().isThrownBy(() -> new Venue(value));
  }

  private static Stream<Arguments> validVenues() {
    return Stream.of(
        Arguments.of("XNYS", "XNYS"),
        Arguments.of("xnas", "XNAS"),
        Arguments.of("arcx-1", "ARCX-1"),
        Arguments.of("BATS.X", "BATS.X"));
  }

  private static Stream<String> invalidVenues() {
    return Stream.of(
        null,
        "",
        "XNYS ",
        "X NYS",
        "XNYS/TEST",
        "XNYS\u00C5",
        "\u00DF",
        "A".repeat(Venue.MAX_LENGTH + 1));
  }
}
