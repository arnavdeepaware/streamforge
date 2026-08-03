package io.streamforge.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CanonicalEventTest {

  private static final SourceIdentity SOURCE = new SourceIdentity("simulator/session-1");
  private static final SequenceNumber SEQUENCE = new SequenceNumber(42);
  private static final Venue VENUE = new Venue("XNAS");
  private static final InstrumentReference INSTRUMENT =
      new InstrumentReference(new InstrumentSymbol("AAPL"));
  private static final RawEventReference RAW_REFERENCE =
      new RawEventReference("capture-1:byte-offset-2048");

  @ParameterizedTest
  @MethodSource("stpPayloads")
  void representsEveryStpMessageWithoutChangingExactValues(MarketEvent payload) {
    EventMetadata metadata =
        EventMetadata.create(
            CanonicalSchemaVersion.V1_0,
            SOURCE,
            VENUE,
            new EventTimestamp(1_000_000_001),
            Optional.of(new EventTimestamp(1_000_000_099)),
            SEQUENCE,
            RAW_REFERENCE);

    CanonicalEvent event = new CanonicalEvent(metadata, INSTRUMENT, payload);

    assertThat(event.metadata().exchangeTimestamp().nanosecondsSinceEpoch())
        .isEqualTo(1_000_000_001);
    assertThat(event.metadata().sequenceNumber()).isEqualTo(SEQUENCE);
    assertThat(event.metadata().eventId()).isEqualTo(EventId.deterministic(SOURCE, SEQUENCE));
    assertThat(event.instrument()).isEqualTo(INSTRUMENT);
    assertThat(event.type()).isEqualTo(payload.type());
    assertThat(event.payload()).isEqualTo(payload);
  }

  @ParameterizedTest
  @MethodSource("missingEnvelopeFields")
  void rejectsMissingEnvelopeFields(
      EventMetadata metadata, InstrumentReference instrument, MarketEvent payload) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CanonicalEvent(metadata, instrument, payload));
  }

  @ParameterizedTest
  @MethodSource("unsupportedVersions")
  void rejectsSchemaVersionsNotImplementedByThisEnvelope(CanonicalSchemaVersion version) {
    EventMetadata metadata =
        EventMetadata.create(
            version,
            SOURCE,
            VENUE,
            new EventTimestamp(1),
            Optional.empty(),
            SEQUENCE,
            RAW_REFERENCE);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CanonicalEvent(metadata, INSTRUMENT, orderAdded()));
  }

  private static Stream<MarketEvent> stpPayloads() {
    return Stream.of(
        orderAdded(),
        new OrderExecuted(new OrderId(1001), new Quantity(40)),
        new OrderCancelled(new OrderId(1001), new Quantity(60)),
        new Trade(
            new TradeId(5001),
            Optional.of(Side.SELL),
            new Quantity(25),
            new FixedDecimal(25_005, 2)));
  }

  private static Stream<Arguments> missingEnvelopeFields() {
    EventMetadata metadata = validMetadata();
    MarketEvent payload = orderAdded();
    return Stream.of(
        Arguments.of(null, INSTRUMENT, payload),
        Arguments.of(metadata, null, payload),
        Arguments.of(metadata, INSTRUMENT, null));
  }

  private static Stream<CanonicalSchemaVersion> unsupportedVersions() {
    return Stream.of(new CanonicalSchemaVersion(1, 1), new CanonicalSchemaVersion(2, 0));
  }

  private static EventMetadata validMetadata() {
    return EventMetadata.create(
        CanonicalSchemaVersion.V1_0,
        SOURCE,
        VENUE,
        new EventTimestamp(1),
        Optional.empty(),
        SEQUENCE,
        RAW_REFERENCE);
  }

  private static OrderAdded orderAdded() {
    return new OrderAdded(
        new OrderId(1001), Side.BUY, new Quantity(100), new FixedDecimal(12_345, 2));
  }
}
