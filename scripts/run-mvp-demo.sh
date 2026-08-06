#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workdir="$(mktemp -d "${TMPDIR:-/tmp}/streamforge-mvp.XXXXXX")"
input_root="$workdir/input"
artifact_root="$workdir/artifacts"
workspace="$workdir/workspace"
control_plane_log="$workdir/control-plane.log"
dashboard_log="$workdir/dashboard.log"
compose_env="$workdir/compose.env"
compose_project="streamforge-mvp-$$"
control_plane_pid=""
dashboard_pid=""

require() {
  command -v "$1" >/dev/null || {
    printf 'Required command is missing: %s\n' "$1" >&2
    exit 1
  }
}

cleanup_on_failure() {
  status=$?
  if [[ $status -ne 0 ]]; then
    [[ -n "$control_plane_pid" ]] && kill "$control_plane_pid" 2>/dev/null || true
    [[ -n "$dashboard_pid" ]] && kill "$dashboard_pid" 2>/dev/null || true
    docker compose -p "$compose_project" --env-file "$compose_env" \
      -f "$root/infrastructure/compose/docker-compose.yml" down >/dev/null 2>&1 || true
    printf 'MVP demo failed. Logs remain at %s and %s\n' \
      "$control_plane_log" "$dashboard_log" >&2
  fi
  exit "$status"
}
trap cleanup_on_failure EXIT
trap 'exit 130' INT TERM

for command in curl docker java npm sed wc; do require "$command"; done
mkdir -p "$input_root" "$artifact_root" "$workspace"
cat >"$compose_env" <<'EOF'
POSTGRES_DB=streamforge
POSTGRES_USER=streamforge
POSTGRES_PASSWORD=change-me-local-only
POSTGRES_PORT=0
EOF

cd "$root"
docker compose -p "$compose_project" --env-file "$compose_env" \
  -f infrastructure/compose/docker-compose.yml up -d postgres
db_port="$(docker compose -p "$compose_project" --env-file "$compose_env" \
  -f infrastructure/compose/docker-compose.yml port postgres 5432 \
  | sed -nE 's/.*:([0-9]+)$/\1/p')"
[[ -n "$db_port" ]] || {
  printf 'Could not determine mapped PostgreSQL port.\n' >&2
  exit 1
}

for attempt in {1..60}; do
  if docker compose -p "$compose_project" --env-file "$compose_env" \
    -f infrastructure/compose/docker-compose.yml exec -T postgres \
    pg_isready -U streamforge -d streamforge >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker compose -p "$compose_project" --env-file "$compose_env" \
  -f infrastructure/compose/docker-compose.yml exec -T postgres \
  pg_isready -U streamforge -d streamforge >/dev/null

./backend/mvnw -f backend/pom.xml -pl control-plane,tick-simulator -am -DskipTests install
npm --prefix web-dashboard ci

CONTROL_PLANE_DB_URL=jdbc:postgresql://localhost:"$db_port"/streamforge \
CONTROL_PLANE_DB_USERNAME=streamforge \
CONTROL_PLANE_DB_PASSWORD=change-me-local-only \
STREAMFORGE_LOCAL_PIPELINE_WORKSPACE="$workspace" \
STREAMFORGE_LOCAL_PIPELINE_INPUT_ROOT="$input_root" \
STREAMFORGE_LOCAL_PIPELINE_ARTIFACT_ROOT="$artifact_root" \
  ./backend/mvnw -f backend/pom.xml -pl control-plane spring-boot:run \
  >"$control_plane_log" 2>&1 &
control_plane_pid=$!
npm --prefix web-dashboard run dev -- --host 127.0.0.1 >"$dashboard_log" 2>&1 &
dashboard_pid=$!

for attempt in {1..60}; do
  if curl --silent --fail http://localhost:8080/actuator/health >/dev/null; then break; fi
  sleep 1
done
curl --silent --fail http://localhost:8080/actuator/health >/dev/null
for attempt in {1..60}; do
  if curl --silent --fail http://127.0.0.1:5173 >/dev/null; then break; fi
  sleep 1
done
curl --silent --fail http://127.0.0.1:5173 >/dev/null

java -cp backend/tick-simulator/target/classes:backend/stp-protocol/target/classes:backend/common-model/target/classes \
  io.streamforge.ticksimulator.TickSimulatorCli \
  --seed 5 --symbols AAPL,MSFT --count 10000 --output "$input_root/ticks.stp"
# A zero declared length is a deterministic malformed STP frame after the valid frames.
printf '\000\000' >>"$input_root/ticks.stp"

cat >"$workdir/pipeline.json" <<'EOF'
{
  "name": "MVP STP monitor demo",
  "description": "Finite simulated STP input with one quarantined malformed frame.",
  "configuration": {
    "input": {"type":"STP_BINARY","path":"ticks.stp","source":"mvp-demo","venue":"XNAS","maximumFrameSize":49},
    "transform": {"schemaVersion":"1.0","operations":[{"op":"add_constant","path":"pipelineLabel","value":{"type":"STRING","value":"mvp-demo"}}]},
    "blueprint": {"schemaVersion":"1.0","output":{"kind":"object","fields":{"event":{"kind":"object","fields":{"type":{"kind":"reference","source":"canonical","path":"payload.type"},"sequence":{"kind":"reference","source":"canonical","path":"metadata.sequenceNumber"}}}}}},
    "output": {"type":"JSONL","path":"normalized.jsonl"}
  }
}
EOF

pipeline_response="$(curl --silent --show-error --fail --request POST \
  --header 'Content-Type: application/json' \
  --data-binary "@$workdir/pipeline.json" \
  http://localhost:8080/api/v1/pipelines)"
pipeline_id="$(printf '%s' "$pipeline_response" | sed -nE 's/^\{"id":"([^"]+)".*/\1/p')"
[[ -n "$pipeline_id" ]] || {
  printf 'Could not read pipeline ID from: %s\n' "$pipeline_response" >&2
  exit 1
}

cat >"$workdir/start.json" <<'EOF'
{"deadLetter":{"policy":"QUARANTINE","includePayload":true,"maximumPayloadBytes":512}}
EOF
run_response="$(curl --silent --show-error --fail --request POST \
  --header 'Content-Type: application/json' \
  --data-binary "@$workdir/start.json" \
  "http://localhost:8080/api/v1/pipelines/$pipeline_id/runs")"
run_id="$(printf '%s' "$run_response" | sed -nE 's/^\{"runId":"([^"]+)".*/\1/p')"
[[ -n "$run_id" ]] || {
  printf 'Could not read run ID from: %s\n' "$run_response" >&2
  exit 1
}

run_url="http://localhost:8080/api/v1/pipelines/$pipeline_id/runs/$run_id"
state=""
for attempt in {1..120}; do
  run_response="$(curl --silent --show-error --fail "$run_url")"
  state="$(printf '%s' "$run_response" | sed -nE 's/.*"state":"([A-Z]+)".*/\1/p')"
  if [[ "$state" == "COMPLETED" || "$state" == "STOPPED" || "$state" == "FAILED" ]]; then
    break
  fi
  sleep 1
done
[[ "$state" == "COMPLETED" ]] || {
  printf 'Expected COMPLETED run, received %s: %s\n' "$state" "$run_response" >&2
  exit 1
}

monitoring_url="$run_url/monitoring"
monitoring="$(curl --silent --show-error --fail "$monitoring_url")"
printf '%s' "$monitoring" | grep -q '"emitted":10000'
printf '%s' "$monitoring" | grep -q '"failed":1'
printf '%s' "$monitoring" | grep -q '"outputAvailable":true'
dead_letters="$(curl --silent --show-error --fail "$run_url/dead-letters")"
dead_letter_count="$(printf '%s' "$dead_letters" | grep -o '"failureId"' | wc -l | tr -d ' ')"
[[ "$dead_letter_count" == "1" ]] || {
  printf 'Expected one dead-letter summary, received %s\n' "$dead_letter_count" >&2
  exit 1
}

download_url="$run_url/output"
curl --silent --show-error --fail "$download_url" --output "$workdir/downloaded-output.jsonl"
line_count="$(wc -l <"$workdir/downloaded-output.jsonl" | tr -d ' ')"
[[ "$line_count" == "10000" ]] || {
  printf 'Expected 10000 output lines, received %s\n' "$line_count" >&2
  exit 1
}

printf '\nMVP demo verified successfully.\n'
printf 'Pipeline page: http://127.0.0.1:5173/pipelines/%s\n' "$pipeline_id"
printf 'Run ID: %s\n' "$run_id"
printf 'Download endpoint: %s\n' "$download_url"
printf 'Artifact root: %s\n' "$artifact_root"
printf 'Control-plane log: %s\nDashboard log: %s\n' "$control_plane_log" "$dashboard_log"
printf 'Shutdown: kill %s %s; docker compose -p %s --env-file %s -f %s down\n' \
  "$control_plane_pid" "$dashboard_pid" "$compose_project" "$compose_env" \
  "$root/infrastructure/compose/docker-compose.yml"
trap - EXIT INT TERM
