package io.streamforge.common.model;

import java.util.Optional;

/** Immutable provenance and ordering information shared by every canonical event. */
public record EventMetadata(
    EventId eventId,
    CanonicalSchemaVersion schemaVersion,
    SourceIdentity source,
    Venue venue,
    EventTimestamp exchangeTimestamp,
    Optional<EventTimestamp> receiveTimestamp,
    SequenceNumber sequenceNumber,
    RawEventReference rawEventReference) {

  public EventMetadata {
    if (eventId == null
        || schemaVersion == null
        || source == null
        || venue == null
        || exchangeTimestamp == null
        || receiveTimestamp == null
        || sequenceNumber == null
        || rawEventReference == null) {
      throw new IllegalArgumentException("event metadata fields must not be null");
    }
    EventId expectedEventId = EventId.deterministic(source, sequenceNumber);
    if (!eventId.equals(expectedEventId)) {
      throw new IllegalArgumentException(
          "event ID does not match source identity and sequence number");
    }
  }

  /** Creates metadata with the deterministic event ID for the supplied source and sequence. */
  public static EventMetadata create(
      CanonicalSchemaVersion schemaVersion,
      SourceIdentity source,
      Venue venue,
      EventTimestamp exchangeTimestamp,
      Optional<EventTimestamp> receiveTimestamp,
      SequenceNumber sequenceNumber,
      RawEventReference rawEventReference) {
    return new EventMetadata(
        EventId.deterministic(source, sequenceNumber),
        schemaVersion,
        source,
        venue,
        exchangeTimestamp,
        receiveTimestamp,
        sequenceNumber,
        rawEventReference);
  }
}
