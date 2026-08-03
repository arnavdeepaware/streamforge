# StreamForge Documentation

- [Project brief](project-brief.md): the problem, intended users, MVP boundary, and non-goals.
- [Architecture](architecture.md): target system design, processing boundaries, module dependencies, risks, and the core MVP definition of done.
- [Roadmap](roadmap.md): phased delivery from parser foundations through distributed and advanced capabilities.
- [MVP monitoring demo](mvp-demo.md): implemented local PostgreSQL, control-plane, dashboard, simulated STP, output, and quarantined-frame walkthrough.
- [Simple Tick Protocol v1](protocol/stp-v1.md): accepted educational binary framing, message layouts, validation, compatibility rules, and implemented Java codec status.
- [Sequence integrity tracking](protocol/sequence-integrity.md): implemented per-source sequence classifications, startup behavior, reset, overflow handling, and parser CLI reporting.
- [STP v1 golden vectors](../schemas/examples/stp-v1-golden-vectors.md): independent byte fixtures for future codec tests.
- [Canonical event schemas](../schemas/canonical/README.md): implemented version-1 Java model mapping, exact-value serialization rules, deterministic event IDs, and schema evolution policy.
- [Canonical event v1 JSON Schema](../schemas/canonical/canonical-event-v1.schema.json): machine-readable serialization contract for canonical events.
- [Transformation configuration v1](../schemas/transformations/README.md): implemented typed configuration, compilation boundary, supported operations, and security restrictions.
- [Transformation configuration v1 JSON Schema](../schemas/transformations/transformation-v1.schema.json): closed machine-readable contract for safe declarative rules.
- [Output blueprint v1](../schemas/transformations/output-blueprint-v1.schema.json): closed machine-readable nested output contract.

## Architecture Decision Records

- [ADR 0001: Monorepo and technology stack](adr/0001-monorepo-and-technology-stack.md)
- [ADR 0002: Canonical event model](adr/0002-canonical-event-model.md)
- [ADR 0003: Control plane and data plane](adr/0003-control-plane-and-data-plane.md)
- [ADR 0004: Safe declarative transformations](adr/0004-safe-declarative-transformations.md)
- [ADR 0005: Simple Tick Protocol Version 1 Wire Format](adr/0005-stp-wire-format.md)
- [ADR 0006: Managed Local Run Artifacts and Explicit Outcomes](adr/0006-managed-local-run-artifacts.md)
