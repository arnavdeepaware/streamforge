# ADR 0002: Canonical Event Model

- Status: Accepted
- Date: 2026-07-31

## Context

StreamForge will ingest simulated binary tick data, CSV, and JSONL and will eventually support several output transports. Direct conversion between each input and each output would create N-by-M implementations with inconsistent validation, precision, and behavior. Market-data debugging also requires access to the original source bytes after normalization fails or changes over time.

## Decision

Every input will pass through Raw Event capture, decoding, validation, and one typed Canonical Event model before transformation or output.

A Raw Event will contain immutable source bytes and provenance. It will be appended to a run-scoped capture before decoding. Canonical Events and diagnostics will hold a stable Raw Event reference rather than embedding the bytes.

The Canonical Event envelope will contain at least:

- Canonical schema version and event type.
- Source identity and source sequence number.
- Event timestamp with nanosecond precision.
- Instrument identity.
- Typed event payload.
- Stable Raw Event reference.

Prices and other exact financial values will use `FixedPoint`, represented by a signed unscaled integer and an explicit scale. Arithmetic will check scale compatibility and overflow. Floating-point values will not represent money or other exact financial quantities. Serializers must preserve the same value and scale without routing through `float` or `double`.

Input adapters and decoders may understand source-specific fields, but they will produce validated canonical types. Transformations and output sinks will consume only Canonical Events. Source identity, source sequence numbers, nanosecond timestamps, schema metadata, and Raw Event references are immutable envelope fields; transformations will not remove, regenerate, truncate, or rewrite them.

Decode, validation, and transformation failures will produce structured diagnostics containing the Raw Event reference. Durable distributed dead-letter handling will be decided separately when distributed processing is introduced.

## Alternatives Considered

### Direct input-to-output converters

Direct converters can be quick for one pair of formats, but they multiply implementations and make validation and precision behavior inconsistent.

### Source-specific event models throughout the pipeline

Keeping source models intact preserves every vendor detail, but it pushes source branching into transformations and sinks. Raw capture provides source fidelity without coupling downstream stages to each source.

### Generic maps for canonical data

String-keyed maps are flexible, but they weaken validation, make schema evolution implicit, and allow incompatible values to reach sinks.

### Floating-point prices or decimal strings as the in-memory model

Floating point cannot preserve exact decimal financial values. Decimal strings preserve text but make checked arithmetic and scale rules indirect. An unscaled integer plus scale makes those rules explicit.

## Consequences

- Each new input needs one path into the canonical model, and each new output needs one path from it.
- Typed canonical schemas and versions become compatibility contracts that require deliberate evolution.
- Some source-specific semantics may not fit the canonical payload immediately; raw capture and versioned typed extensions provide the escape hatch.
- Raw capture consumes storage and will require retention and integrity policies before production use.
- Fixed-point operations require explicit scale and overflow handling, but they avoid silent precision loss.
- Stable Raw Event references make failures reproducible without carrying source bytes through every processing stage.
