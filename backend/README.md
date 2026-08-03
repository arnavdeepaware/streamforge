# Backend Build

The backend is a Java 21 Maven multi-module build. It includes immutable market-data value types, STP v1 framing and codecs, a deterministic tick simulator with a local TCP generator-to-parser path, and a local in-process pipeline runner in `pipeline-runtime`. The runner streams STP binary, JSONL, and CSV files through transformation and optional output blueprints into JSONL or CSV files. It does not provide control-plane services.

## Local Pipeline Runner

`io.streamforge.pipelineruntime.PipelineCli` loads one strict saved JSON configuration with
`--config <path>`. It processes each input incrementally with direct backpressure, reports final
received, parsed, normalized, filtered, emitted, and failed counters, and retains bounded,
source-located failures. Cancellation or output failure aborts staged file output rather than
publishing a partial destination.

Optional local dead-letter handling is configured with a root `deadLetter` object. `QUARANTINE`
writes staged JSONL records containing a deterministic failure ID, pipeline and schema versions,
stage, source location, safe error message, retryability, and an optional bounded payload fragment.
`SKIP` continues without durable storage; `FAIL_FAST` stops at the first record-level failure.
For example:

```json
{
  "pipelineId": "sample-normalization",
  "pipelineVersion": "1",
  "deadLetter": {
    "policy": "QUARANTINE",
    "path": "dead-letter.jsonl",
    "includePayload": true,
    "maximumPayloadBytes": 4096
  }
}
```

Payload capture is opt-in, byte-bounded, and redacts common `password`, `token`, `secret`, and API
key assignments. Quarantined parse, normalization, transformation, and blueprint failures do not
stop independent later records. A primary output failure is marked retryable and remains terminal.

[`../schemas/examples/pipeline-aapl-jsonl-v1.json`](../schemas/examples/pipeline-aapl-jsonl-v1.json)
is a checked-in JSONL-to-JSONL sample that applies the AAPL output blueprint. Its expected output
is [`../schemas/examples/pipeline-aapl-jsonl-golden-output.jsonl`](../schemas/examples/pipeline-aapl-jsonl-golden-output.jsonl).

## Requirements

- A JDK from 21 through 26. The build compiles with Java 21 release compatibility.
- Internet access the first time Maven Wrapper downloads Maven and dependency artifacts.

## Commands

From the repository root:

```sh
./backend/mvnw -f backend/pom.xml verify
```

From the `backend/` directory:

```sh
./mvnw verify
```

The reactor includes `common-model`, `stp-protocol`, `tick-simulator`, `parser-engine`, `transform-engine`, `pipeline-runtime`, `control-plane`, and `stream-worker`.

## Control Plane

The control plane is a Spring Boot and PostgreSQL persistence service. It stores validated,
credential-free pipeline definition components and revisions, but does not execute pipelines or
provide authentication. Local PostgreSQL startup and service commands are documented in
[`control-plane/README.md`](control-plane/README.md).

## Tick Simulator

Build the simulator and its reactor dependencies, then write a finite binary STP fixture:

```sh
./backend/mvnw -f backend/pom.xml -pl tick-simulator -am package
java -cp backend/tick-simulator/target/classes:backend/stp-protocol/target/classes:backend/common-model/target/classes \
  io.streamforge.ticksimulator.TickSimulatorCli \
  --seed 5 --symbols AAPL,MSFT --count 100 --output ticks.stp
```

Use `--output -` to write binary frames to standard output. Run the final command with `--help` for event-distribution, timestamp, and continuous-mode options. The classpath separator above is for POSIX shells.

## Local TCP Demo

Build the two modules:

```sh
./backend/mvnw -f backend/pom.xml -pl tick-simulator,parser-engine -am package
```

In terminal 1, start a server that exits after serving its first finite client:

```sh
java -cp backend/tick-simulator/target/classes:backend/stp-protocol/target/classes:backend/common-model/target/classes \
  io.streamforge.ticksimulator.TickTcpServerCli \
  --host 127.0.0.1 --port 9010 --seed 5 --symbols AAPL,MSFT --count 10 --rate 0
```

In terminal 2, connect and print parsed events:

```sh
java -cp backend/parser-engine/target/classes:backend/stp-protocol/target/classes:backend/common-model/target/classes \
  io.streamforge.parserengine.StpParserCli \
  --host 127.0.0.1 --port 9010 \
  --report-sequence-integrity --source demo-session
```

Sequence integrity reporting is optional; it prints structured expected, gap, duplicate, and late/out-of-order events for the named logical source/session. The TCP server writes directly to each client socket. TCP flow control blocks generation for a slow client instead of accumulating an unbounded application queue. The classpath separators above are for POSIX shells.
