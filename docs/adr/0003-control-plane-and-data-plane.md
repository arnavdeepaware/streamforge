# ADR 0003: Control Plane and Data Plane

- Status: Accepted
- Date: 2026-07-31

## Context

Pipeline configuration and lifecycle operations are low-volume and consistency-oriented. Market-data ingestion and transformation are high-volume and latency-sensitive. Combining both paths would let control-plane availability, database access, or dashboard traffic interfere with event processing.

The core MVP will be a local process and will not yet implement either a remote control plane or distributed workers.

## Decision

StreamForge will separate control-plane operations from data-plane event processing.

The control plane will eventually:

- Validate, version, and manage pipeline configurations and lifecycle intent.
- Store durable control state in PostgreSQL.
- Serve the React dashboard through a dedicated API.
- Deliver immutable, versioned configuration snapshots to workers through a control-plane API.

The data plane will:

- Run adapters, decoders, validators, Canonical Events, transformations, raw capture, diagnostics, and output sinks.
- Process market events without routing them through the control plane.
- Expose Prometheus-compatible metrics for independent scraping.
- Keep processing an already accepted configuration without querying the control-plane database.

Workers will not read or write the PostgreSQL schema directly. Control-plane and worker communication will use versioned API contracts. Redis may later support replaceable ephemeral state, but it will not hold required durable configuration and will not be needed by the parser milestone or core local MVP.

Kafka-compatible streaming and distributed workers will be introduced only after the local pipeline works. The same canonical and transformation contracts will apply in local and distributed runtimes.

## Alternatives Considered

### One combined service

A combined service is simpler to deploy initially, but it couples event throughput to configuration, database, and UI workloads. The local MVP can still run as one process without erasing the logical boundary.

### Workers sharing the control-plane database

Shared database access avoids an API, but it couples workers to schema migrations, expands database credentials, and makes control-plane storage part of the data path.

### Route market events through the control plane

Central routing could simplify visibility, but it would turn a low-volume management service into a throughput bottleneck and a broad failure domain.

### Begin with Kafka and distributed workers

A distributed-first design would test scaling assumptions early, but it would slow validation of parsing, canonicalization, and deterministic transformation behavior.

## Consequences

- Control-plane outages will not automatically stop workers that already have valid configuration snapshots.
- Versioned APIs and configuration compatibility will require explicit design and testing.
- PostgreSQL credentials and schema knowledge remain confined to the control plane.
- Metrics provide observation without sending high-volume events through management services.
- Local and distributed hosts must preserve the same data-plane contracts to avoid divergent behavior.
- The separation adds deployment concepts later, but keeps scaling and failure domains explicit.
