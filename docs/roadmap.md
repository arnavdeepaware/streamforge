# StreamForge Roadmap

## Version 1.0: local product

The v1 codebase implements the canonical model, STP protocol and simulator, STP/CSV/JSONL input
adapters, mandatory raw capture, declarative transformations, output blueprints, JSONL/CSV/Parquet
sinks, local dead letters, PostgreSQL control plane, dashboard, health probes, Prometheus metrics,
and Docker Compose packaging.

Release certification is tracked in the [v1 checklist](v1-release-checklist.md). A release tag is
created only after every environment-dependent gate passes and the user explicitly authorizes Git
operations.

## Post-v1

- Authentication and authorization.
- Distributed workers and durable remote artifact storage.
- Kafka-compatible transports and optional Redis-backed replaceable state.
- WebSocket delivery and richer live operational views.
- Replay controls, Stream Inspector, and global dead-letter browsing.
- Schema editing and approval workflows.
- Order-book reconstruction with explicit venue/sequence semantics.

These items are plans, not implemented product behavior.
