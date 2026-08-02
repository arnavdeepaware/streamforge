# Simple Tick Protocol Version 1

Status: Accepted specification; the Java encoder and one-frame decoder are implemented in the backend `stp-protocol` module.

## Purpose

Simple Tick Protocol (STP) v1 is an educational, length-prefixed binary protocol for simulated market events. It defines four event types with fixed-width fields so future codecs can be deterministic and easy to inspect.

STP v1 does not define transport, authentication, compression, session negotiation, order-book state, or recovery. Stream configuration outside the frame identifies the protocol version.

## Conventions

- Byte offsets are zero-based from the first byte of a frame and are inclusive.
- Every multi-byte field uses network byte order (big-endian).
- `uint16`, `uint32`, and `uint64` denote unsigned wire integers.
- `int64` denotes a signed two's-complement wire integer.
- Java decoders will store accepted `uint32` values in `long`.
- Java decoders will store accepted `uint64` values in `long` only after rejecting values above `Long.MAX_VALUE` (`9,223,372,036,854,775,807`).
- A frame consists of a two-byte length field followed by exactly `length` bytes.

## Framing

The unsigned 16-bit length at offsets `0-1` excludes its own two bytes. It includes every byte after the length field: the one-byte message type and the complete payload.

Therefore:

```text
total frame size = 2 + encoded length
encoded length = 1 message-type byte + payload bytes
```

Length zero is invalid because it cannot contain a message type. For known v1 message types, the encoded length must equal the exact value in the message summary.

### Common Frame Fields

Known v1 messages share the sequence and timestamp envelope below. A complete unknown-type frame is skippable solely from its length and is not required to contain this envelope.

| Field | Offset | Wire type | Width | Validation | Semantics |
| --- | ---: | --- | ---: | --- | --- |
| Length | `0-1` | `uint16` | 2 bytes | `1..65,535`; a known type must use its exact v1 length | Number of bytes following this field, including message type and payload |
| Message type | `2` | byte | 1 byte | `A`, `E`, `C`, or `T` for a known v1 message; other values are unknown types | Selects the payload layout |
| Sequence number | `3-10` | `uint64` | 8 bytes | `1..Long.MAX_VALUE` | Stream ordering key; strictly increasing per stream, with gaps permitted |
| Event timestamp | `11-18` | `uint64` | 8 bytes | `0..Long.MAX_VALUE` | Unix-epoch timestamp in nanoseconds; need not be monotonic |

### Message Summary

| Message | Type byte | Encoded length | Payload bytes after type | Total frame size |
| --- | --- | ---: | ---: | ---: |
| Add Order | ASCII `A` (`0x41`) | 47 | 46 | 49 bytes |
| Execute Order | ASCII `E` (`0x45`) | 29 | 28 | 31 bytes |
| Cancel Order | ASCII `C` (`0x43`) | 29 | 28 | 31 bytes |
| Trade | ASCII `T` (`0x54`) | 47 | 46 | 49 bytes |

## Shared Field Encodings

### Unsigned Values

Sequence numbers, timestamps, order IDs, and trade IDs are `uint64` on the wire but intentionally use only the nonnegative Java `long` domain. A decoder must reject any value with the high bit set because it exceeds `Long.MAX_VALUE`.

Quantities are `uint32` on the wire and must be decoded into a Java `long`. Valid quantities are `1..4,294,967,295`; zero is invalid.

### Symbols

A symbol occupies exactly eight bytes. It contains one to eight printable, non-space ASCII bytes in the range `0x21..0x7E`, followed only by zero or more ASCII space (`0x20`) padding bytes. A decoder strips trailing padding.

Empty symbols, bytes outside `0x21..0x7E` before padding, and any non-space byte after padding begins are invalid. Space is padding only and cannot occur within a decoded symbol.

### Sides

The only valid side bytes are ASCII `B` (`0x42`) and ASCII `S` (`0x53`). Add Order uses the order side. Trade uses the aggressor side.

### Prices

A price is the exact pair of a signed `int64` mantissa and a `uint8` scale. The scale must be in `0..18`.

```text
price = mantissa * 10^-scale
```

The mantissa and scale must be preserved without floating-point conversion or implicit scale normalization. Future arithmetic must be checked for overflow.

## Add Order (`A`)

An Add Order frame has encoded length 47 and total size 49 bytes.

| Field | Offset | Wire type | Width | Validation | Semantics |
| --- | ---: | --- | ---: | --- | --- |
| Length | `0-1` | `uint16` | 2 bytes | Must equal 47 | Bytes after the length field |
| Message type | `2` | byte | 1 byte | Must equal ASCII `A` (`0x41`) | Identifies Add Order |
| Sequence number | `3-10` | `uint64` | 8 bytes | `1..Long.MAX_VALUE` | Strictly increasing stream sequence |
| Event timestamp | `11-18` | `uint64` | 8 bytes | `0..Long.MAX_VALUE` | Unix-epoch nanoseconds |
| Order ID | `19-26` | `uint64` | 8 bytes | `0..Long.MAX_VALUE` | Source-assigned order identifier |
| Symbol | `27-34` | ASCII bytes | 8 bytes | One to eight `0x21..0x7E` bytes, then trailing `0x20` padding only | Instrument symbol with padding stripped |
| Side | `35` | byte | 1 byte | ASCII `B` or `S` | Order side |
| Quantity | `36-39` | `uint32` | 4 bytes | `1..4,294,967,295` | Added order quantity |
| Price mantissa | `40-47` | `int64` | 8 bytes | Any signed 64-bit value | Exact price mantissa |
| Price scale | `48` | `uint8` | 1 byte | `0..18` | Number of decimal fractional digits |

## Execute Order (`E`)

An Execute Order frame has encoded length 29 and total size 31 bytes.

| Field | Offset | Wire type | Width | Validation | Semantics |
| --- | ---: | --- | ---: | --- | --- |
| Length | `0-1` | `uint16` | 2 bytes | Must equal 29 | Bytes after the length field |
| Message type | `2` | byte | 1 byte | Must equal ASCII `E` (`0x45`) | Identifies Execute Order |
| Sequence number | `3-10` | `uint64` | 8 bytes | `1..Long.MAX_VALUE` | Strictly increasing stream sequence |
| Event timestamp | `11-18` | `uint64` | 8 bytes | `0..Long.MAX_VALUE` | Unix-epoch nanoseconds |
| Order ID | `19-26` | `uint64` | 8 bytes | `0..Long.MAX_VALUE` | Referenced order identifier |
| Executed quantity | `27-30` | `uint32` | 4 bytes | `1..4,294,967,295` | Quantity executed by this event |

Wire validation does not require prior order state. An unknown order ID or an executed quantity greater than a known remaining quantity is a later order-book semantic error, not a framing or field-validation error.

## Cancel Order (`C`)

A Cancel Order frame has encoded length 29 and total size 31 bytes.

| Field | Offset | Wire type | Width | Validation | Semantics |
| --- | ---: | --- | ---: | --- | --- |
| Length | `0-1` | `uint16` | 2 bytes | Must equal 29 | Bytes after the length field |
| Message type | `2` | byte | 1 byte | Must equal ASCII `C` (`0x43`) | Identifies Cancel Order |
| Sequence number | `3-10` | `uint64` | 8 bytes | `1..Long.MAX_VALUE` | Strictly increasing stream sequence |
| Event timestamp | `11-18` | `uint64` | 8 bytes | `0..Long.MAX_VALUE` | Unix-epoch nanoseconds |
| Order ID | `19-26` | `uint64` | 8 bytes | `0..Long.MAX_VALUE` | Referenced order identifier |
| Canceled quantity | `27-30` | `uint32` | 4 bytes | `1..4,294,967,295` | Quantity canceled by this event |

Wire validation does not require prior order state. An unknown order ID or a canceled quantity greater than a known remaining quantity is a later order-book semantic error, not a framing or field-validation error.

## Trade (`T`)

A Trade frame has encoded length 47 and total size 49 bytes.

| Field | Offset | Wire type | Width | Validation | Semantics |
| --- | ---: | --- | ---: | --- | --- |
| Length | `0-1` | `uint16` | 2 bytes | Must equal 47 | Bytes after the length field |
| Message type | `2` | byte | 1 byte | Must equal ASCII `T` (`0x54`) | Identifies Trade |
| Sequence number | `3-10` | `uint64` | 8 bytes | `1..Long.MAX_VALUE` | Strictly increasing stream sequence |
| Event timestamp | `11-18` | `uint64` | 8 bytes | `0..Long.MAX_VALUE` | Unix-epoch nanoseconds |
| Trade ID | `19-26` | `uint64` | 8 bytes | `0..Long.MAX_VALUE` | Source-assigned trade identifier |
| Symbol | `27-34` | ASCII bytes | 8 bytes | One to eight `0x21..0x7E` bytes, then trailing `0x20` padding only | Instrument symbol with padding stripped |
| Aggressor side | `35` | byte | 1 byte | ASCII `B` or `S` | Side of the aggressing order |
| Quantity | `36-39` | `uint32` | 4 bytes | `1..4,294,967,295` | Traded quantity |
| Price mantissa | `40-47` | `int64` | 8 bytes | Any signed 64-bit value | Exact trade-price mantissa |
| Price scale | `48` | `uint8` | 1 byte | `0..18` | Number of decimal fractional digits |

## Decoding Procedure

1. Read exactly two bytes as the unsigned frame length.
2. Reject length zero because the frame cannot contain a message type.
3. Wait for or read exactly the declared number of following bytes. If end-of-input occurs first, report a truncated frame and stop; do not scan payload bytes for a speculative next prefix.
4. Read the message type at offset 2.
5. If the type is unknown, skip the complete declared frame without validating its payload.
6. If the type is known but the declared length is not its exact v1 length, consume the complete declared frame and report a malformed length.
7. Decode the known layout in network byte order and validate every field.
8. Validate that the sequence number is strictly greater than the preceding accepted sequence number for the same stream. Gaps are valid.
9. Emit a decoded event only when framing, fields, and stream sequencing are valid.

## Errors and Alignment

| Condition | Classification | Required behavior |
| --- | --- | --- |
| Fewer than two bytes remain for the prefix | Truncated length field | Report truncation; consume no speculative frame |
| Encoded length is zero | Malformed length | Report the error; the two-byte prefix is the complete malformed framing unit |
| Known type has a non-v1 length | Malformed length | If all declared bytes are available, consume exactly `2 + length` bytes and report the error |
| Declared frame is incomplete at end-of-input | Truncated frame | Report truncation and stop without searching payload bytes for resynchronization |
| Complete frame has an unknown type | Unsupported message type | Skip exactly `2 + length` bytes without payload validation; continue at the next prefix |
| Sequence, timestamp, or ID exceeds `Long.MAX_VALUE` | Invalid field | Reject the known frame after consuming its declared bytes |
| Sequence is zero, duplicated, or decreases | Invalid stream sequence | Reject the event; preserve framing alignment |
| Quantity is zero | Invalid field | Reject the known frame after consuming its declared bytes |
| Price scale exceeds 18 | Invalid field | Reject the known frame after consuming its declared bytes |
| Side is not `B` or `S` | Invalid field | Reject the known frame after consuming its declared bytes |
| Symbol is empty, non-ASCII, non-printable, contains a space before content ends, or has non-trailing padding | Invalid field | Reject the known frame after consuming its declared bytes |

For any complete known frame with invalid fields, the decoder consumes the complete declared frame before reporting the error. Framing remains aligned for the next prefix.

## Sequencing and Time

Sequence numbers determine event order within one configured stream. They must start at any positive value and then increase strictly; gaps are permitted. The protocol does not define ordering across streams.

Event timestamps represent Unix-epoch nanoseconds and may be equal, decrease, or differ from ingestion time. A timestamp therefore never replaces the sequence number as the ordering key.

## Compatibility and Versioning

STP v1 has no in-frame version byte. The enclosing stream configuration must identify the stream as STP v1 before decoding begins, and a stream cannot change versions without an external configuration boundary.

Compatible v1 additions may assign previously unused message-type bytes. Such additions must retain the common sequence/timestamp envelope when they represent events. Existing v1 layouts and meanings never change. Readers that do not recognize a newly assigned type remain able to skip its complete frame using the length prefix.

Any incompatible change to framing, byte order, an existing field layout, validation semantics, or an existing message meaning requires STP v2.

## Test Fixtures

The independent byte fixtures for all four messages are documented in [STP v1 golden vectors](../../schemas/examples/stp-v1-golden-vectors.md).
