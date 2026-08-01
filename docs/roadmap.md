# StreamForge Roadmap

StreamForge currently contains documentation and directory scaffolding only. The phases below describe intended delivery order, not implemented features.

## Phase 1: Parser Foundation

Build the source-to-canonical boundary before introducing services or distributed infrastructure.

- Define immutable canonical event types, Raw Event provenance, and exact fixed-point values.
- Specify the simulated tick protocol and build deterministic test fixtures and a simulator.
- Decode simulated binary tick data, CSV, and JSONL through format-specific adapters.
- Validate candidates before constructing Canonical Events.
- Capture raw bytes and provenance append-only before decoding.
- Produce structured diagnostics that retain Raw Event references.

Redis, Kafka, PostgreSQL, the control plane, and the web dashboard will not be required in this phase.

## Phase 2: Core Local MVP

Compose a useful single-node pipeline and satisfy the [core MVP definition of done](architecture.md#core-mvp-definition-of-done).

- Add versioned, typed declarative filter, map, derive, and project operations.
- Compose adapters, decoders, validation, canonical events, transformations, and sinks in the local pipeline runtime.
- Emit JSONL, CSV, and Parquet from the canonical model.
- Expose Prometheus-compatible, bounded-cardinality metrics.
- Make invalid input and transformation failures observable through deterministic local diagnostics.
- Document and test commands that run the complete pipeline on a clean checkout.

This is the core MVP boundary. It will not depend on remote services.

## Phase 3: Control-Plane Experience

Add remote configuration and operational workflows after the local pipeline is dependable.

- Store durable pipeline definitions, versions, and lifecycle state in PostgreSQL.
- Expose a control-plane API that provides immutable, versioned configuration snapshots.
- Build the React dashboard with strict TypeScript and a dedicated API client layer.
- Add WebSocket delivery for approved live-client use cases.
- Keep market events out of the control plane and keep workers isolated from the PostgreSQL schema.

## Phase 4: Distributed Extensions

Scale the proven data-plane boundaries without changing canonical event semantics.

- Introduce Kafka-compatible input and output transport.
- Host pipeline runtimes in independently scalable stream workers.
- Add partitioning, retry, and horizontal-scaling policies.
- Add durable dead-letter infrastructure for records that cannot be processed.
- Introduce Redis only where replaceable ephemeral live state has a demonstrated use; correctness must not depend on it.

## Phase 5: Advanced Capabilities

Build richer operational and market-data behavior on the stable pipeline.

- Add replay controls backed by retained raw captures.
- Add live operational views and deeper pipeline diagnostics.
- Add order-book reconstruction with explicit venue and sequence semantics.

Each phase must keep documentation aligned with working commands and record major new decisions in an ADR before implementation commits to them.
