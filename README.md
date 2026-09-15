# StreamForge 1.0

StreamForge is a local, single-node market-data normalization product. It ingests STP binary,
CSV, or JSONL files; captures every input byte before parsing; normalizes through an exact typed
canonical model; applies validated declarative transformations and blueprints; and atomically
publishes JSONL, CSV, or explicitly typed Parquet output. A PostgreSQL control plane and React
dashboard provide versioned configuration, run lifecycle, bounded monitoring, dead-letter
inspection, and artifact downloads.

## Start the product

Requirements: Docker with Compose support. From the repository root:

```sh
docker compose up --build
```

Then open:

- Dashboard: <http://localhost:5173>
- API and OpenAPI: <http://localhost:8080/swagger-ui/index.html>
- Readiness: <http://localhost:8080/actuator/health/readiness>
- Prometheus metrics: <http://localhost:8080/actuator/prometheus>

PostgreSQL is available only inside the Compose network. Database state, temporary workspace, and
run artifacts use named volumes. By default, pipeline input paths are relative to
`schemas/examples`. Set `STREAMFORGE_INPUT_DIR` before startup to mount another host directory
read-only:

```sh
STREAMFORGE_INPUT_DIR=/absolute/path/to/input docker compose up --build
```

Raw captures and output artifacts are retained until the corresponding Docker volume is removed;
v1 retention is manual.

## Developer verification

Host builds are developer alternatives, not the supported user installation path. Use Java 21 and
Node 22:

```sh
./backend/mvnw -f backend/pom.xml verify
npm --prefix web-dashboard ci
npm --prefix web-dashboard run format:check
npm --prefix web-dashboard run lint
npm --prefix web-dashboard run test
npm --prefix web-dashboard run build
./scripts/run-stp-benchmarks.sh
```

With Docker available, the release-level stack, artifact, metrics, and restart smoke test is:

```sh
./scripts/run-v1-acceptance.sh
```

`make check` runs the backend and dashboard quality suites. Testcontainers integration tests run
when Docker is available and otherwise report skips. See [the release checklist](docs/v1-release-checklist.md),
[architecture](docs/architecture.md), and [backend reference](backend/README.md).

## V1 boundary

Implemented: exact canonical values, STP/CSV/JSONL input, mandatory immutable run capture,
declarative transforms and blueprints, JSONL/CSV/Parquet output, local dead-letter policies,
PostgreSQL lifecycle persistence, REST/SSE monitoring, bounded Prometheus metrics, storage health,
and the dashboard.

Deferred: authentication, distributed workers, Kafka, Redis, WebSockets, replay controls,
order-book reconstruction, Stream Inspector, global dead-letter browsing, and schema editing. The
`stream-worker` directory is a post-v1 placeholder and is excluded from the 1.0 Maven reactor.
