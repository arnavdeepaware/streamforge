# StreamForge 1.0 Architecture

StreamForge is a local single-node application with three Compose services: the static dashboard,
the Java control plane/data plane, and PostgreSQL. PostgreSQL stores definitions, immutable
revisions, lifecycle state, reports, and managed artifact references; market events never flow
through the database.

## Processing path

Every supported input follows the same boundary:

`source file -> immutable run capture -> adapter/decoder -> canonical event -> declarative transform -> blueprint -> staged output`

The runner finishes the capture and manifest before it opens the captured file for parsing.
Canonical events point to `capture:<run-id>:frame|line|row:<n>`. JSONL, CSV, and Parquet sinks
consume the resulting canonical/blueprint document rather than parsing source formats. Finite
outputs are staged beside their destination and published only on successful completion.

The canonical model uses signed 64-bit integers for identifiers, quantities, sequence numbers,
and nanoseconds. Money uses a signed mantissa plus scale; it never passes through binary
floating-point. Parquet has an explicit column contract and no inference.

## Components

- `common-model`: immutable canonical event and exact value types.
- `stp-protocol`: bounded STP v1 framing and codecs.
- `tick-simulator`: deterministic STP fixtures and local TCP source tooling.
- `parser-engine`: STP, CSV, and JSONL adapters plus sequence integrity.
- `transform-engine`: validated typed transformation AST and output blueprints.
- `pipeline-runtime`: synchronous local composition, raw capture, output sinks, and CLI.
- `control-plane`: PostgreSQL persistence, REST/OpenAPI, local execution, SSE, health, and metrics.
- `web-dashboard`: pipeline configuration, field mapping, run health, dead letters, and downloads.

`stream-worker` is a post-v1 placeholder excluded from the Maven reactor.

## Operational behavior

HTTP input paths must remain beneath a configured readable input root. Workspace and artifacts are
server-owned writable roots; persisted artifact paths are relative, and downloads re-check real
path containment to reject traversal and symlink escapes. Raw captures survive completed, stopped,
failed, and partially processed runs. V1 cleanup is manual.

`/actuator/health/liveness` checks the process. `/actuator/health/readiness` requires PostgreSQL
and readable/writable storage. `/actuator/prometheus` exposes bounded aggregate run, stage,
processing-time, sequence-anomaly, and active-run metrics without identifiers or user data as
labels.

## Deferred architecture

Authentication, remote workers, Kafka, Redis, WebSockets, replay controls, order-book
reconstruction, schema editing, Stream Inspector, and global dead-letter browsing are post-v1.
