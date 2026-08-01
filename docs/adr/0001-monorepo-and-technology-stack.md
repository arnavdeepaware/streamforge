# ADR 0001: Monorepo and Technology Stack

- Status: Accepted
- Date: 2026-07-31

## Context

StreamForge will combine market-data parsing, a canonical domain model, transformation and pipeline runtimes, a control-plane service, a web dashboard, schemas, infrastructure, and cross-cutting tests. These parts need coordinated contracts and releases while the architecture is still forming. The first milestone must remain runnable without distributed infrastructure.

This decision records the intended stack. No application or service described here has been implemented yet.

## Decision

- Keep backend, dashboard, schemas, documentation, infrastructure, scripts, and tests in one monorepo.
- Build the backend with Java 21 as a Maven multi-module project using the `io.streamforge` package prefix.
- Build the dashboard with React and TypeScript in strict mode, using npm and a committed `package-lock.json` when it is initialized.
- Use PostgreSQL for durable control-plane configuration and lifecycle state in the later control-plane phase.
- Allow Redis only as an optional later store for replaceable ephemeral live state. The parser milestone and core local MVP must not require it.
- Introduce Kafka-compatible streaming only after the local pipeline is working end to end.
- Expose Prometheus-compatible metrics from data-plane runtimes.
- Keep runtime and domain boundaries independent of any one transport so the local pipeline can establish behavior before distributed deployment.

## Alternatives Considered

### Separate repositories

Separate repositories could isolate release cycles, but they would add coordination overhead while canonical contracts, schemas, and module boundaries are evolving.

### Gradle or a non-Java backend

Gradle and other backend stacks could satisfy the technical requirements, but Java 21 and Maven provide an explicit, conventional multi-module structure for this project and align with the chosen backend guidance.

### A single-language or server-rendered UI stack

Serving all UI behavior from the backend could reduce tooling, but React with strict TypeScript provides a clear client boundary for the planned operational dashboard.

### Kafka-first or Redis-required architecture

Starting with distributed dependencies could exercise production-like topology earlier, but it would obscure parser and canonical-model correctness behind operational complexity.

## Consequences

- Cross-component contracts and documentation can evolve atomically in one repository.
- Module boundaries and CI checks will need to prevent accidental coupling inside the monorepo.
- Java and TypeScript introduce two build ecosystems that must be documented and maintained separately.
- The local-first sequence reduces early operational complexity but postpones validation of Kafka partitioning and distributed-worker behavior.
- PostgreSQL and Redis remain absent from parser and core-MVP execution paths; future work must preserve that separation.
