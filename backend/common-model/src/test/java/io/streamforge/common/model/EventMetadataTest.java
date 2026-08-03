package io.streamforge.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EventMetadataTest {

  @ParameterizedTest
  @MethodSource("receiveTimestamps")
  void distinguishesAnAbsentReceiveTimestampFromEpochZero(
      Optional<EventTimestamp> receiveTimestamp) {
    EventMetadata metadata = create(receiveTimestamp);

    assertThat(metadata.receiveTimestamp()).isEqualTo(receiveTimestamp);
  }

  @ParameterizedTest
  @MethodSource("invalidMetadata")
  void rejectsMissingFieldsAndMismatchedEventIds(
      EventId eventId,
      CanonicalSchemaVersion version,
      SourceIdentity source,
      Venue venue,
      EventTimestamp exchangeTimestamp,
      Optional<EventTimestamp> receiveTimestamp,
      SequenceNumber sequence,
      RawEventReference rawReference) {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new EventMetadata(
                    eventId,
                    version,
                    source,
                    venue,
                    exchangeTimestamp,
                    receiveTimestamp,
                    sequence,
                    rawReference));
  }

  private static Stream<Optional<EventTimestamp>> receiveTimestamps() {
    return Stream.of(Optional.empty(), Optional.of(new EventTimestamp(0)));
  }

  private static Stream<Arguments> invalidMetadata() {
    SourceIdentity source = new SourceIdentity("source-1");
    SequenceNumber sequence = new SequenceNumber(1);
    EventId eventId = EventId.deterministic(source, sequence);
    CanonicalSchemaVersion version = CanonicalSchemaVersion.V1_0;
    Venue venue = new Venue("XNAS");
    EventTimestamp timestamp = new EventTimestamp(0);
    Optional<EventTimestamp> receiveTimestamp = Optional.empty();
    RawEventReference rawReference = new RawEventReference("capture:0");
    return Stream.of(
        Arguments.of(
            null, version, source, venue, timestamp, receiveTimestamp, sequence, rawReference),
        Arguments.of(
            eventId, null, source, venue, timestamp, receiveTimestamp, sequence, rawReference),
        Arguments.of(
            eventId, version, null, venue, timestamp, receiveTimestamp, sequence, rawReference),
        Arguments.of(
            eventId, version, source, null, timestamp, receiveTimestamp, sequence, rawReference),
        Arguments.of(
            eventId, version, source, venue, null, receiveTimestamp, sequence, rawReference),
        Arguments.of(eventId, version, source, venue, timestamp, null, sequence, rawReference),
        Arguments.of(
            eventId, version, source, venue, timestamp, receiveTimestamp, null, rawReference),
        Arguments.of(eventId, version, source, venue, timestamp, receiveTimestamp, sequence, null),
        Arguments.of(
            EventId.deterministic(source, new SequenceNumber(2)),
            version,
            source,
            venue,
            timestamp,
            receiveTimestamp,
            sequence,
            rawReference));
  }

  private static EventMetadata create(Optional<EventTimestamp> receiveTimestamp) {
    return EventMetadata.create(
        CanonicalSchemaVersion.V1_0,
        new SourceIdentity("source-1"),
        new Venue("XNAS"),
        new EventTimestamp(1),
        receiveTimestamp,
        new SequenceNumber(1),
        new RawEventReference("capture:0"));
  }
}
