# ADR 0005: Simple Tick Protocol Version 1 Wire Format

- Status: Accepted
- Date: 2026-08-01

## Context

StreamForge needs an educational binary market-data format for the parser foundation. The format must be deterministic enough for simulators, independent golden vectors, malformed-input tests, and future Java codecs while remaining simple to inspect by hand.

The protocol must preserve nanosecond timestamps, source sequence numbers, and exact fixed-point prices. It also needs framing that lets readers skip unsupported messages without confusing payload bytes with the next frame. The Java implementation will need explicit handling for unsigned wire values because Java has no unsigned `long` storage type suitable for the project's intended APIs.

No STP codec is implemented at the time of this decision.

## Decision

STP v1 will use fixed-layout, length-prefixed frames in network byte order. Each frame will begin with an unsigned 16-bit length that excludes the length field itself and includes the one-byte message type plus the complete payload. Known v1 messages will be Add Order (`A`), Execute Order (`E`), Cancel Order (`C`), and Trade (`T`). Unknown complete frames will be skipped using their declared length.

Every known event will carry a `uint64` sequence number and a `uint64` Unix-epoch nanosecond timestamp in a common envelope. Sequence numbers will be positive and strictly increasing per stream, with gaps permitted. Timestamps will not determine stream order.

Sequence numbers, timestamps, order IDs, and trade IDs will use `uint64` wire fields restricted to `0..Long.MAX_VALUE`, except sequence numbers, which will use `1..Long.MAX_VALUE`. Values above `Long.MAX_VALUE` will be rejected. Quantities will use positive `uint32` semantics and will be represented in Java as `long`.

Symbols will use eight ASCII bytes containing one to eight printable non-space characters followed only by trailing space padding. Sides will be `B` or `S`. Prices will use a signed `int64` mantissa and a `uint8` scale from 0 through 18. The mantissa/scale pair will remain exact and will never be converted through `float` or `double`.

Execute and Cancel decoding will not require an in-memory order book. Unknown order IDs and quantities that exceed known remaining order quantity will be order-book semantic errors rather than wire-format errors.

STP v1 will not include an in-frame version byte. External stream configuration will identify the version. Compatible additions may assign unused message-type bytes while retaining the common event envelope; existing v1 layouts and meanings will not change. Incompatible changes will require STP v2.

The normative field layouts, validation rules, error behavior, and length values are defined in the [STP v1 specification](../protocol/stp-v1.md). Independent examples are defined in the [STP v1 golden vectors](../../schemas/examples/stp-v1-golden-vectors.md).

## Alternatives Considered

### Variable-length or TLV Fields

Variable-length or type-length-value fields would make optional evolution easier, but they would add parser state and more malformed-input cases to an educational first protocol. Fixed layouts make offsets and fixture expectations explicit. Length-prefixed whole frames still permit unknown message types to be skipped.

### Little-endian Encoding

Little-endian encoding could align with common desktop processors, but network byte order is conventional for wire protocols and avoids making the format host-specific. The educational value of a stable cross-platform convention outweighs negligible conversion cost.

### Unrestricted `uint64` with `BigInteger`

Supporting the full unsigned 64-bit range with `BigInteger` would preserve every theoretical wire value, but it would complicate APIs and arithmetic for no expected educational-domain benefit. Restricting values to nonnegative Java `long` keeps future implementations explicit and efficient while requiring validation of the high bit.

### Variable-width Symbols

Variable-width symbols would reduce padding and allow longer identifiers, but they would require another length convention or delimiter and would make message offsets variable. An eight-byte padded ASCII field is sufficient for the intended fixtures and keeps layouts directly inspectable.

## Consequences

- Future encoders and decoders will have exact frame sizes, offsets, and validation rules to implement.
- Golden vectors can validate byte order, length handling, and exact numeric preservation independently of implementation details.
- Unknown message types can be skipped without decoding their payload, supporting compatible message additions.
- Fixed layouts simplify parsers but spend padding bytes on short symbols and cannot represent symbols longer than eight bytes.
- Restricting `uint64` fields to `Long.MAX_VALUE` deliberately rejects part of the wire type's mathematical range.
- Exact fixed-point storage avoids floating-point loss, but future arithmetic must check overflow and enforce scale rules.
- External stream configuration must reliably identify STP v1 because frames carry no version byte.
- Order-book consistency remains outside wire decoding, so a structurally valid event may still fail later semantic processing.
