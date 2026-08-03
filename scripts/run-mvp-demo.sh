#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workdir="$(mktemp -d "${TMPDIR:-/tmp}/streamforge-mvp.XXXXXX")"
control_plane_log="$workdir/control-plane.log"
dashboard_log="$workdir/dashboard.log"

require() {
  command -v "$1" >/dev/null || {
    printf 'Required command is missing: %s\n' "$1" >&2
    exit 1
  }
}

for command in curl docker java npm; do require "$command"; done

if [[ ! -f "$root/infrastructure/compose/.env" ]]; then
  cp "$root/infrastructure/compose/.env.example" "$root/infrastructure/compose/.env"
fi

cd "$root"
docker compose --env-file infrastructure/compose/.env \
  -f infrastructure/compose/docker-compose.yml up -d postgres

./backend/mvnw -f backend/pom.xml -pl control-plane,tick-simulator -am package
npm --prefix web-dashboard ci

CONTROL_PLANE_DB_URL=jdbc:postgresql://localhost:5432/streamforge \
CONTROL_PLANE_DB_USERNAME=streamforge \
CONTROL_PLANE_DB_PASSWORD=change-me-local-only \
  ./backend/mvnw -f backend/pom.xml -pl control-plane -am spring-boot:run \
  >"$control_plane_log" 2>&1 &
control_plane_pid=$!
npm --prefix web-dashboard run dev -- --host 127.0.0.1 >"$dashboard_log" 2>&1 &
dashboard_pid=$!

for attempt in {1..60}; do
  if curl --silent --fail http://localhost:8080/actuator/health >/dev/null; then break; fi
  sleep 1
done
curl --silent --fail http://localhost:8080/actuator/health >/dev/null

java -cp backend/tick-simulator/target/classes:backend/stp-protocol/target/classes:backend/common-model/target/classes \
  io.streamforge.ticksimulator.TickSimulatorCli \
  --seed 5 --symbols AAPL,MSFT --count 10000 --output "$workdir/ticks.stp"
# A zero declared length is a deterministic malformed STP frame quarantined after valid frames.
printf '\000\000' >>"$workdir/ticks.stp"

cat >"$workdir/pipeline.json" <<EOF
{
  "name": "MVP STP monitor demo",
  "description": "Finite simulated STP input with one quarantined malformed frame.",
  "configuration": {
    "input": {"type":"STP_BINARY","path":"$workdir/ticks.stp","source":"mvp-demo","venue":"XNAS","maximumFrameSize":49},
    "transform": {"schemaVersion":"1.0","operations":[{"op":"add_constant","path":"pipelineLabel","value":{"type":"STRING","value":"mvp-demo"}}]},
    "blueprint": {"schemaVersion":"1.0","output":{"kind":"object","fields":{"event":{"kind":"object","fields":{"type":{"kind":"reference","source":"canonical","path":"payload.type"},"sequence":{"kind":"reference","source":"canonical","path":"metadata.sequenceNumber"}}}}}},
    "output": {"type":"JSONL","path":"$workdir/output.jsonl"}
  }
}
EOF

pipeline_response="$(curl --silent --show-error --fail --request POST \
  --header 'Content-Type: application/json' \
  --data-binary "@$workdir/pipeline.json" \
  http://localhost:8080/api/v1/pipelines)"
pipeline_id="$(printf '%s' "$pipeline_response" | sed -nE 's/.*"id":"([^"]+)".*/\1/p')"
if [[ -z "$pipeline_id" ]]; then
  printf 'Could not read the created pipeline ID. Response: %s\n' "$pipeline_response" >&2
  exit 1
fi

cat >"$workdir/start.json" <<EOF
{"deadLetter":{"policy":"QUARANTINE","path":"$workdir/dead-letter.jsonl","includePayload":true,"maximumPayloadBytes":512}}
EOF
run_response="$(curl --silent --show-error --fail --request POST \
  --header 'Content-Type: application/json' \
  --data-binary "@$workdir/start.json" \
  "http://localhost:8080/api/v1/pipelines/$pipeline_id/runs")"
run_id="$(printf '%s' "$run_response" | sed -nE 's/.*"runId":"([^"]+)".*/\1/p')"
if [[ -z "$run_id" ]]; then
  printf 'Could not read the started run ID. Response: %s\n' "$run_response" >&2
  exit 1
fi

printf '\nMVP demo is running.\n'
printf 'Dashboard: http://127.0.0.1:5173/pipelines/%s\n' "$pipeline_id"
printf 'Run ID: %s\n' "$run_id"
printf 'Output: %s\n' "$workdir/output.jsonl"
printf 'Dead letters: %s\n' "$workdir/dead-letter.jsonl"
printf 'Logs: %s and %s\n' "$control_plane_log" "$dashboard_log"
printf 'Stop services when finished: kill %s %s; docker compose --env-file infrastructure/compose/.env -f infrastructure/compose/docker-compose.yml down\n' "$control_plane_pid" "$dashboard_pid"
