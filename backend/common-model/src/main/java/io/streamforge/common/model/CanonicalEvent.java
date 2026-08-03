package io.streamforge.common.model;

/** Versioned canonical envelope shared by transformation and output boundaries. */
public record CanonicalEvent(
    EventMetadata metadata, InstrumentReference instrument, MarketEvent payload) {

  public CanonicalEvent {
    if (metadata == null || instrument == null || payload == null) {
      throw new IllegalArgumentException("canonical event fields must not be null");
    }
    if (!metadata.schemaVersion().equals(CanonicalSchemaVersion.V1_0)) {
      throw new IllegalArgumentException("this canonical model supports schema version 1.0 only");
    }
  }

  /** Returns the payload discriminator included by the serialized event contract. */
  public CanonicalEventType type() {
    return payload.type();
  }
}
