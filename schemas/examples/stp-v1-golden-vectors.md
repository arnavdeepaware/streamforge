# STP v1 Golden Vectors

These fixtures independently describe complete [Simple Tick Protocol v1](../../docs/protocol/stp-v1.md) frames for future codec tests. No STP codec is implemented yet.

All multi-byte values use network byte order. The two-byte unsigned length excludes itself and includes the one-byte message type plus payload. Offsets are zero-based and inclusive.

## Add Order (`A`)

- Encoded length: 47 (`0x002F`)
- Total byte count: 49
- Price value: `12345 * 10^-2 = 123.45`

| Field | Offset | Width | Hex bytes | Decoded value |
| --- | ---: | ---: | --- | --- |
| Length | `0-1` | 2 | `00 2F` | 47 |
| Message type | `2` | 1 | `41` | `A` (Add Order) |
| Sequence number | `3-10` | 8 | `00 00 00 00 00 00 00 01` | 1 |
| Timestamp (ns) | `11-18` | 8 | `00 00 00 00 3B 9A CA 00` | 1,000,000,000 |
| Order ID | `19-26` | 8 | `00 00 00 00 00 00 03 E9` | 1001 |
| Symbol | `27-34` | 8 | `41 41 50 4C 20 20 20 20` | `AAPL` plus four trailing spaces |
| Side | `35` | 1 | `42` | `B` (buy order) |
| Quantity | `36-39` | 4 | `00 00 00 64` | 100 |
| Price mantissa | `40-47` | 8 | `00 00 00 00 00 00 30 39` | 12,345 |
| Price scale | `48` | 1 | `02` | 2 |

Grouped by field:

```text
00 2F | 41 | 00 00 00 00 00 00 00 01 | 00 00 00 00 3B 9A CA 00 | 00 00 00 00 00 00 03 E9 | 41 41 50 4C 20 20 20 20 | 42 | 00 00 00 64 | 00 00 00 00 00 00 30 39 | 02
```

Contiguous frame:

```text
00 2F 41 00 00 00 00 00 00 00 01 00 00 00 00 3B 9A CA 00 00 00 00 00 00 00 03 E9 41 41 50 4C 20 20 20 20 42 00 00 00 64 00 00 00 00 00 00 30 39 02
```

## Execute Order (`E`)

- Encoded length: 29 (`0x001D`)
- Total byte count: 31

| Field | Offset | Width | Hex bytes | Decoded value |
| --- | ---: | ---: | --- | --- |
| Length | `0-1` | 2 | `00 1D` | 29 |
| Message type | `2` | 1 | `45` | `E` (Execute Order) |
| Sequence number | `3-10` | 8 | `00 00 00 00 00 00 00 02` | 2 |
| Timestamp (ns) | `11-18` | 8 | `00 00 00 00 3B 9A CA 64` | 1,000,000,100 |
| Order ID | `19-26` | 8 | `00 00 00 00 00 00 03 E9` | 1001 |
| Executed quantity | `27-30` | 4 | `00 00 00 28` | 40 |

Grouped by field:

```text
00 1D | 45 | 00 00 00 00 00 00 00 02 | 00 00 00 00 3B 9A CA 64 | 00 00 00 00 00 00 03 E9 | 00 00 00 28
```

Contiguous frame:

```text
00 1D 45 00 00 00 00 00 00 00 02 00 00 00 00 3B 9A CA 64 00 00 00 00 00 00 03 E9 00 00 00 28
```

## Cancel Order (`C`)

- Encoded length: 29 (`0x001D`)
- Total byte count: 31

| Field | Offset | Width | Hex bytes | Decoded value |
| --- | ---: | ---: | --- | --- |
| Length | `0-1` | 2 | `00 1D` | 29 |
| Message type | `2` | 1 | `43` | `C` (Cancel Order) |
| Sequence number | `3-10` | 8 | `00 00 00 00 00 00 00 03` | 3 |
| Timestamp (ns) | `11-18` | 8 | `00 00 00 00 3B 9A CA C8` | 1,000,000,200 |
| Order ID | `19-26` | 8 | `00 00 00 00 00 00 03 E9` | 1001 |
| Canceled quantity | `27-30` | 4 | `00 00 00 3C` | 60 |

Grouped by field:

```text
00 1D | 43 | 00 00 00 00 00 00 00 03 | 00 00 00 00 3B 9A CA C8 | 00 00 00 00 00 00 03 E9 | 00 00 00 3C
```

Contiguous frame:

```text
00 1D 43 00 00 00 00 00 00 00 03 00 00 00 00 3B 9A CA C8 00 00 00 00 00 00 03 E9 00 00 00 3C
```

## Trade (`T`)

- Encoded length: 47 (`0x002F`)
- Total byte count: 49
- Price value: `25005 * 10^-2 = 250.05`

| Field | Offset | Width | Hex bytes | Decoded value |
| --- | ---: | ---: | --- | --- |
| Length | `0-1` | 2 | `00 2F` | 47 |
| Message type | `2` | 1 | `54` | `T` (Trade) |
| Sequence number | `3-10` | 8 | `00 00 00 00 00 00 00 04` | 4 |
| Timestamp (ns) | `11-18` | 8 | `00 00 00 00 3B 9A CB 2C` | 1,000,000,300 |
| Trade ID | `19-26` | 8 | `00 00 00 00 00 00 13 89` | 5001 |
| Symbol | `27-34` | 8 | `4D 53 46 54 20 20 20 20` | `MSFT` plus four trailing spaces |
| Aggressor side | `35` | 1 | `53` | `S` (sell aggressor) |
| Quantity | `36-39` | 4 | `00 00 00 19` | 25 |
| Price mantissa | `40-47` | 8 | `00 00 00 00 00 00 61 AD` | 25,005 |
| Price scale | `48` | 1 | `02` | 2 |

Grouped by field:

```text
00 2F | 54 | 00 00 00 00 00 00 00 04 | 00 00 00 00 3B 9A CB 2C | 00 00 00 00 00 00 13 89 | 4D 53 46 54 20 20 20 20 | 53 | 00 00 00 19 | 00 00 00 00 00 00 61 AD | 02
```

Contiguous frame:

```text
00 2F 54 00 00 00 00 00 00 00 04 00 00 00 00 3B 9A CB 2C 00 00 00 00 00 00 13 89 4D 53 46 54 20 20 20 20 53 00 00 00 19 00 00 00 00 00 00 61 AD 02
```

## Fixture Assertions

A future codec test using these vectors must independently assert all of the following:

- The first two bytes decode to the stated encoded length.
- The total byte count equals `2 + encoded length`.
- The message-type byte selects the stated message.
- Every numeric field decodes in network byte order to the stated value.
- Timestamp values retain nanosecond units and sequence numbers retain their exact values.
- Symbol padding is removed without altering symbol content.
- Quantities decode to nonnegative Java `long` values.
- Price mantissa and scale remain separate exact values without floating-point conversion.
