# MVP Monitoring Demo

`scripts/run-mvp-demo.sh` is a self-checking local demonstration of PostgreSQL, the control plane,
the Vite dashboard, deterministic STP generation, managed pipeline execution, quarantine, and
finite output download. It uses the local streaming file runner; Kafka and TCP pipeline inputs are
not involved.

## Prerequisites

- Docker Desktop or another Docker-compatible daemon
- JDK 21 through 26
- Node.js compatible with `web-dashboard/package.json`
- `curl`, `sed`, `wc`, and a POSIX-compatible shell

## Run

From the repository root:

```sh
./scripts/run-mvp-demo.sh
```

The script creates isolated temporary input, workspace, and artifact roots. It waits for
PostgreSQL, the control-plane health endpoint, and the dashboard before creating a pipeline. The
pipeline reads `ticks.stp` relative to the managed input root and writes its output and dead-letter
data beneath a server-owned run directory in the managed artifact root.

The generated input contains 10,000 valid deterministic STP frames followed by one malformed
zero-length frame. The script waits up to two minutes for a terminal state and fails unless all of
these assertions hold:

- lifecycle state is `COMPLETED`;
- emitted count is `10,000` and failed count is `1`;
- exactly one recent dead-letter summary is available;
- output is downloadable and contains exactly `10,000` JSONL lines.

On success, the script leaves the services running and prints the pipeline page, run ID, download
endpoint, artifact root, service logs, and an exact shutdown command. On setup or verification
failure, it exits nonzero and stops child services and the Compose stack; logs remain in the
printed temporary directory.

## Monitoring

The pipeline detail page restores the latest run, including terminal runs after a control-plane
restart. It displays lifecycle state, exact counters, event rate, integer nanosecond latency,
direct-backpressure queue depth (`0`), sequence anomalies, and recent safe dead-letter summaries.
The output link appears only when the run completed and the managed artifact still exists.

SSE delivery is decoupled from pipeline processing and reconnects with capped exponential backoff.
The server retains all active observations plus at most 100 terminal observations for 24 hours.
The browser keeps at most 120 metric snapshots. Historical rate samples are not persisted; after a
restart, final counters and at most 50 recent managed dead-letter summaries are restored while rate
history starts empty.

## Boundaries

The control plane is unauthenticated and intended only for local use. HTTP-started pipelines may
only read files beneath `STREAMFORGE_LOCAL_PIPELINE_INPUT_ROOT`. Outputs and quarantine records are
server-owned beneath `STREAMFORGE_LOCAL_PIPELINE_ARTIFACT_ROOT`; host-absolute paths are neither
persisted nor returned. CLI-only pipeline execution still accepts explicit local paths.

Redis, Kafka, authentication, remote workers, and distributed artifact storage are outside this
MVP.
