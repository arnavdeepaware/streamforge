# StreamForge Backend 1.0

The Java 21 Maven reactor contains the exact canonical model, STP protocol/simulator,
STP/CSV/JSONL parsers, declarative transformation engine, local pipeline runtime, and PostgreSQL
control plane. `stream-worker` is excluded as post-v1.

## Verify

From the repository root:

```sh
./backend/mvnw -f backend/pom.xml verify
```

With Docker available this includes PostgreSQL/Testcontainers integration tests. The benchmark
smoke command is:

```sh
./scripts/run-stp-benchmarks.sh
```

## Local pipeline CLI

The CLI always generates a UUID run ID and captures the source before parsing. Artifacts default
to `.streamforge/artifacts/<run-id>`:

```sh
java -cp backend/pipeline-runtime/target/classes io.streamforge.pipelineruntime.PipelineCli \
  --config /path/to/pipeline.json \
  --artifact-root /path/to/artifacts
```

Its report prints the run ID, capture location, exact counters, and terminal status. Configuration
supports STP binary, JSONL, and CSV input; JSONL, CSV, and explicit-schema Parquet output; optional
safe transformation/blueprint documents; and `SKIP`, `QUARANTINE`, or `FAIL_FAST` dead-letter
behavior.

Parquet column types are `STRING`, `BOOLEAN`, `INT64`, `TIMESTAMP_NANOS`, and `FIXED_DECIMAL`.
Compression is `UNCOMPRESSED`, `SNAPPY` (default), or `ZSTD`. Fixed decimals require scale 0–18;
other types reject scale. Required fields, scalar type compatibility, exact decimal rescaling, and
overflow are enforced.

The public output section is explicit and does not infer a schema:

```json
{
  "type": "PARQUET",
  "path": "normalized.parquet",
  "parquet": {
    "compression": "SNAPPY",
    "rowGroupSizeBytes": 134217728,
    "columns": [
      {
        "name": "exchange_timestamp",
        "path": "metadata.exchangeTimestamp",
        "type": "TIMESTAMP_NANOS",
        "required": true
      },
      {
        "name": "price",
        "path": "payload.price",
        "type": "FIXED_DECIMAL",
        "scale": 4,
        "required": false
      }
    ]
  }
}
```

The supported end-user startup remains `docker compose up --build`; see the
[control-plane README](control-plane/README.md) only for host-development alternatives.
