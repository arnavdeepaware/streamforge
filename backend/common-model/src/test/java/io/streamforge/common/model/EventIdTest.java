package io.streamforge.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EventIdTest {

  @ParameterizedTest
  @MethodSource("stableIdentities")
  void derivesStableIdsFromSourceAndSequence(String source, long sequence, String expectedId) {
    EventId first = EventId.deterministic(new SourceIdentity(source), new SequenceNumber(sequence));
    EventId second =
        EventId.deterministic(new SourceIdentity(source), new SequenceNumber(sequence));

    assertThat(first).isEqualTo(second);
    assertThat(first.value()).isEqualTo(expectedId);
  }

  @ParameterizedTest
  @MethodSource("differentIdentities")
  void changesWhenSourceOrSequenceChanges(
      String firstSource, long firstSequence, String secondSource, long secondSequence) {
    assertThat(
            EventId.deterministic(
                new SourceIdentity(firstSource), new SequenceNumber(firstSequence)))
        .isNotEqualTo(
            EventId.deterministic(
                new SourceIdentity(secondSource), new SequenceNumber(secondSequence)));
  }

  @ParameterizedTest
  @MethodSource("invalidEventIds")
  void rejectsMalformedEventIds(String value) {
    assertThatIllegalArgumentException().isThrownBy(() -> new EventId(value));
  }

  private static Stream<Arguments> stableIdentities() {
    return Stream.of(
        Arguments.of(
            "simulator/session-1",
            1L,
            "4c2fbb1cc2338df9a8cd2ffd5f1426675058af1a9539ed1a0ffa543ac318b65c"),
        Arguments.of(
            "XNYS:ITCH:2026-08-03",
            Long.MAX_VALUE,
            "8c623019b1b0c9ddb694176f2aff8b8e6fde527216195787ef6b97ee070e4508"));
  }

  private static Stream<Arguments> differentIdentities() {
    return Stream.of(
        Arguments.of("source-a", 1L, "source-a", 2L),
        Arguments.of("source-a", 1L, "source-b", 1L),
        Arguments.of("SOURCE", 1L, "source", 1L));
  }

  private static Stream<String> invalidEventIds() {
    return Stream.of(null, "", "0".repeat(63), "0".repeat(65), "A".repeat(64), "g".repeat(64));
  }
}
