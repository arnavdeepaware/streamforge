# StreamForge v1.0 Release Checklist

The codebase targets release `1.0.0`. A release may be declared complete only when every gate below
passes on the release revision. Do not create the release commit or tag without explicit Git
authorization.

## Product Gates

- [x] All inputs normalize through the canonical model and preserve exact values.
- [x] Every run captures its source before parsing and retains the capture for every outcome.
- [x] JSONL, CSV, and explicit-schema Parquet outputs are supported.
- [x] PostgreSQL restores terminal run state and managed output/capture references.
- [x] Dashboard exposes only implemented v1 workflows.
- [x] Prometheus metrics have bounded, non-user-controlled labels.
- [x] Liveness, database/storage readiness, and safe artifact downloads are implemented.
- [x] The supported local stack is defined by root `compose.yaml`.

## Verification Gates

- [ ] Java 21 backend verification passes with zero failures and zero skips with Docker available.
- [ ] Node 22 formatting, lint, unit tests, and production build pass. (The same checks pass locally
  on Node 23.)
- [ ] STP benchmark smoke passes on Java 21. (It passes locally on Java 26.)
- [ ] Docker Compose stack becomes ready and survives a control-plane restart with state intact.
- [ ] Nine-format input/output matrix and malformed/exact-value cases pass against the full stack.
- [ ] Browser smoke covers create, validate, run, restore, dead letters, output, and raw download.
- [ ] README commands have been copied and executed successfully from a clean checkout.
- [ ] Working tree is clean.
- [ ] Authorized `v1.0.0` release commit and tag exist.

## Deferred Beyond v1

Authentication, distributed workers, Kafka, Redis, WebSockets, replay controls, order-book
reconstruction, Stream Inspector, global dead-letter browsing, and schema editing are explicitly
outside this release. Artifact retention is manual for local v1.
