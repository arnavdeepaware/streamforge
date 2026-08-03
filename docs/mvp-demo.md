# MVP Monitoring Demo

`scripts/run-mvp-demo.sh` starts PostgreSQL, the control plane, and the Vite dashboard; creates a
finite STP-to-JSONL pipeline; generates deterministic simulated STP frames; and starts the run. It
uses the local pipeline runtime's streaming file reader, not Kafka or a TCP pipeline input.

## Prerequisites

- Docker Desktop or another Docker-compatible daemon
- JDK 21 through 26
- Node.js compatible with `web-dashboard/package.json`
- `curl`, `sed`, and a POSIX-compatible shell

## Run

From the repository root:

```sh
./scripts/run-mvp-demo.sh
```

The script creates a temporary directory and prints its path, the pipeline ID, run ID, output file,
dead-letter file, and service PIDs. It deliberately appends a zero-length STP frame after 10,000
valid deterministic simulator frames. The valid frames are written to the JSONL output; the final
malformed frame is quarantined to the printed dead-letter JSONL file.

Open the printed dashboard URL. On the pipeline detail page, the **Pipeline health** panel shows
lifecycle state, received/parsed/emitted/filtered/failed counters, integer event rate, integer
nanosecond latency summary, direct-backpressure queue depth (`0`), sequence gaps, duplicates, and
the bounded received-event history. Expand **Recent dead-letter events** to inspect the safe,
payload-bounded preview for the intentional malformed frame. The finite output is downloadable
once the run is complete.

The server emits monitoring snapshots over SSE. The dashboard reconnects with capped exponential
backoff if that connection is interrupted; it does not retain raw events in the browser.

When finished, use the exact stop command printed by the script. The script does not remove its
temporary directory so that output and dead-letter files remain available for inspection.

## Current Boundaries

This is a local single-node demonstration. Monitoring history and recent dead-letter summaries are
bounded in memory for the current control-plane process; durable run lifecycle and final counters
remain in PostgreSQL. Redis, Kafka, remote workers, and distributed dead-letter storage are not
part of this MVP demo.
