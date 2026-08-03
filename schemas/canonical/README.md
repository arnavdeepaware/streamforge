# Canonical Event Schemas

[`canonical-event-v1.schema.json`](canonical-event-v1.schema.json) defines the implemented JSON
shape of the Java canonical model in `backend/common-model`. It is a serialization contract, not a
claim that a JSON serializer or adapter has been implemented.

## Version 1.0

The envelope contains immutable metadata, an instrument reference, and one typed payload. Version
1.0 supports `ORDER_ADDED`, `ORDER_EXECUTED`, `ORDER_CANCELLED`, `TRADE`, and `QUOTE` payloads.
STP v1 Add, Execute, Cancel, and Trade messages map to the first four payloads without dropping
their order or trade identifier, symbol, side, quantity, price mantissa and scale, sequence number,
or nanosecond timestamp.

The JSON field names match the Java model's concepts, with deliberate scalar boundary mappings for
small value records: `EventId`, `SourceIdentity`, `Venue`, `RawEventReference`,
`InstrumentSymbol`, `OrderId`, `TradeId`, and `SequenceNumber` serialize as their scalar `value`;
`EventTimestamp` serializes as its exact `nanosecondsSinceEpoch`; and `Quantity` serializes as its
positive scalar value. `FixedDecimal` and `CanonicalSchemaVersion` remain structured pairs. The
payload's computed `type()` is serialized as its discriminator.

`Optional.empty()` values are omitted rather than serialized as zero or `null`. This applies to
`receiveTimestamp`, `Trade.aggressorSide`, and the absent side of a one-sided `Quote`. Quantities
are always positive, while order and trade IDs and timestamps may deliberately be zero. A future
serializer must implement these boundary mappings explicitly; no serializer is currently included.

JSON integer values represent exact mathematical integers. Serializers and consumers must not
route timestamps, sequence numbers, identifiers, quantities, or price mantissas through a binary
floating-point type. A fixed decimal is the pair `mantissa` and `scale`; its value is `mantissa x
10^-scale`.

## Event IDs

An event ID is the lowercase hexadecimal SHA-256 digest of these bytes in order:

1. ASCII `streamforge:canonical-event-id:v1` followed by one zero byte.
2. The source identity's UTF-8 byte length as a four-byte signed nonnegative integer in network
   byte order.
3. The source identity's UTF-8 bytes.
4. The positive sequence number as an eight-byte signed Java `long` in network byte order.

Source identity is case-sensitive and identifies one stable stream or session. A source must use a
new identity before restarting its sequence numbers. The Java `EventMetadata` constructor verifies
the digest instead of accepting an unrelated ID.

## Versioning Rules

- The `major.minor` pair is part of every event. The current Java model and schema accept exactly
  `1.0`.
- A minor version may add optional metadata, payload fields, or payload types while preserving all
  existing meanings. A new schema document and matching Java support are required before emitting
  that version.
- Removing a field, changing a field's type or meaning, changing exact-value semantics, or changing
  event-ID derivation requires a new major version.
- Producers must emit only a version they implement. Consumers must reject unsupported major or
  minor versions rather than silently interpreting them as another version.
- Raw-event references remain stable across schema versions so an event can be investigated or
  normalized again from its captured source record.
