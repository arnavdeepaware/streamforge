# Sequence Integrity Tracking

`parser-engine` implements `SequenceIntegrityTracker`, a transport- and storage-independent in-memory classifier for accepted STP message sequence numbers. It is intended for one logical source or session at a time and does not implement replay, persistence, or recovery.

## Startup And Scope

Each `SequenceSource` starts with an expected sequence of `1`.

- The first received sequence `1` is `EXPECTED`.
- A first received sequence greater than `1` is `GAP_DETECTED`; the event reports `1` as expected and the number of missing sequences.
- Trackers keep source state independent, so a gap or reset for one session does not affect another.
- `reset(source)` forgets that source. Its next sequence is again classified against startup expectation `1`.

The tracker operates on already framed, field-valid `SequenceNumber` values. It does not use a TCP connection, file, database, or event payload.

## Classifications

Every call returns a `SequenceIntegrityEvent` with source, received sequence, classification, expected sequence when one exists, and missing count.

| Classification | Condition | State Change |
| --- | --- | --- |
| `EXPECTED` | Received sequence equals the next expected sequence | Advance expected sequence by one |
| `GAP_DETECTED` | Received sequence is greater than expected | Report the missing count and advance expected to the received sequence plus one |
| `DUPLICATE` | Received sequence equals the most recently accepted sequence | Do not advance |
| `LATE_OR_OUT_OF_ORDER` | Received sequence is below expected but is not the most recently accepted sequence | Do not advance |

To stay bounded, the tracker stores only the next expected sequence and the most recently accepted sequence for each source. A nonconsecutive replay below expected is therefore reported as `LATE_OR_OUT_OF_ORDER`, even if that number occurred earlier; retaining every prior sequence solely to label it a duplicate is intentionally out of scope.

## Overflow

`Long.MAX_VALUE` is the largest supported STP sequence. When it is accepted, the tracker records that no next expected sequence exists rather than incrementing and overflowing. A later repeat of `Long.MAX_VALUE` is `DUPLICATE`; any other valid sequence is `LATE_OR_OUT_OF_ORDER`.

## Parser CLI

The parser CLI preserves its strict decoder behavior by default. To print structured integrity events, enable reporting:

```sh
java -cp backend/parser-engine/target/classes:backend/stp-protocol/target/classes:backend/common-model/target/classes \
  io.streamforge.parserengine.StpParserCli \
  --host 127.0.0.1 --port 9010 \
  --report-sequence-integrity --source demo-session
```

In reporting mode, framing and field validation remain enabled, but sequence rejection is delegated to the tracker so duplicate and late messages can be classified and printed. Existing parse failures are still reported to standard error.
