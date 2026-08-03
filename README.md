# StreamForge

StreamForge is a planned configurable platform for ingesting real-time market data, normalizing it into a canonical event model, applying safe declarative transformations, and delivering it to multiple output formats and transports.

The Java 21 backend Maven reactor includes immutable market-data value types, STP v1 codecs, a deterministic tick simulator, a local TCP generator-to-parser path, streaming JSONL and CSV output sinks, and a local in-process pipeline runner for STP binary, JSONL, and CSV files. The React/Vite dashboard reads pipeline definitions and schema catalog entries from the versioned control-plane API; graphical editing and operational views remain unimplemented.

The local pipeline runner can also quarantine record-level failures to a staged JSONL dead-letter
file with deterministic IDs and opt-in, bounded payload capture. Distributed dead-letter handling
is not implemented.

The control plane is a separate Spring Boot service that persists validated, credential-free
pipeline definitions and revisions in PostgreSQL. It does not execute pipelines or authenticate
users. See [`backend/control-plane/README.md`](backend/control-plane/README.md) for local startup.

Verify the backend from the repository root:

```sh
./backend/mvnw -f backend/pom.xml verify
```

Generate a deterministic binary STP fixture on a POSIX shell:

```sh
./backend/mvnw -f backend/pom.xml -pl tick-simulator -am package
java -cp backend/tick-simulator/target/classes:backend/stp-protocol/target/classes:backend/common-model/target/classes \
  io.streamforge.ticksimulator.TickSimulatorCli \
  --seed 5 --symbols AAPL,MSFT --count 100 --output ticks.stp
```

## Local TCP Demo

Build both local components first:

```sh
./backend/mvnw -f backend/pom.xml -pl tick-simulator,parser-engine -am package
```

Terminal 1 starts a server that exits after serving its first finite client:

```sh
java -cp backend/tick-simulator/target/classes:backend/stp-protocol/target/classes:backend/common-model/target/classes \
  io.streamforge.ticksimulator.TickTcpServerCli \
  --host 127.0.0.1 --port 9010 --seed 5 --symbols AAPL,MSFT --count 10 --rate 0
```

Terminal 2 connects, incrementally decodes the STP frames, and prints each parsed event:

```sh
java -cp backend/parser-engine/target/classes:backend/stp-protocol/target/classes:backend/common-model/target/classes \
  io.streamforge.parserengine.StpParserCli \
  --host 127.0.0.1 --port 9010 \
  --report-sequence-integrity --source demo-session
```

The classpath separators in these examples are for POSIX shells.

Install dashboard dependencies from the repository root:

```sh
npm --prefix web-dashboard ci
```

Run the dashboard development server:

```sh
npm --prefix web-dashboard run dev
```

The dashboard uses `VITE_CONTROL_PLANE_API_URL`, defaulting to `/api/v1`. During local Vite development, that relative path is proxied to `http://localhost:8080`; override the proxy target with `VITE_CONTROL_PLANE_PROXY_TARGET` when needed. See [`web-dashboard/.env.example`](web-dashboard/.env.example).

Run dashboard checks:

```sh
npm --prefix web-dashboard run lint
npm --prefix web-dashboard run test
npm --prefix web-dashboard run build
```

## Validation

Run every local quality check from the repository root:

```sh
make check
```

Run one area at a time:

```sh
make backend-check
make web-check
```

`backend-check` runs Maven Wrapper verification, including Java formatting enforcement. `web-check` runs `npm ci`, Prettier format checking, ESLint, Vitest in non-watch mode, and the Vite production build. GitHub Actions runs these same targets with Maven and npm caches.

Remove generated build outputs with:

```sh
make clean
```
