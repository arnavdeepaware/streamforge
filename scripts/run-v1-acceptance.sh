#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
project="streamforge-v1-${GITHUB_RUN_ID:-local}-$$"
workdir="$(mktemp -d "${TMPDIR:-/tmp}/streamforge-v1-acceptance.XXXXXX")"

cleanup() {
  status=$?
  docker compose -p "$project" -f "$root/compose.yaml" logs --no-color >"$workdir/compose.log" 2>&1 || true
  docker compose -p "$project" -f "$root/compose.yaml" down --volumes >/dev/null 2>&1 || true
  if [[ $status -ne 0 ]]; then
    printf 'V1 acceptance failed; Compose logs: %s\n' "$workdir/compose.log" >&2
  else
    rm -rf "$workdir"
  fi
  exit "$status"
}
trap cleanup EXIT INT TERM

for command in cmp curl docker grep od sed; do
  command -v "$command" >/dev/null || {
    printf 'Required command is missing: %s\n' "$command" >&2
    exit 1
  }
done

cd "$root"
docker compose -p "$project" up --build --detach --wait
curl --silent --show-error --fail http://localhost:8080/actuator/health/liveness | grep -q '"status":"UP"'
curl --silent --show-error --fail http://localhost:8080/actuator/health/readiness | grep -q '"status":"UP"'
curl --silent --show-error --fail http://localhost:5173/ >/dev/null

cat >"$workdir/pipeline.json" <<'EOF'
{
  "name": "V1 Docker acceptance",
  "description": "Exercises exact Parquet output and immutable input capture.",
  "configuration": {
    "input": {"type":"JSONL","path":"pipeline-aapl-input.jsonl","mode":"CONTINUE_WITH_ERRORS"},
    "output": {
      "type":"PARQUET",
      "path":"normalized.parquet",
      "parquet": {
        "compression":"SNAPPY",
        "rowGroupSizeBytes":1048576,
        "columns":[
          {"name":"exchange_timestamp","path":"metadata.exchangeTimestamp","type":"TIMESTAMP_NANOS","required":true},
          {"name":"sequence_number","path":"metadata.sequenceNumber","type":"INT64","required":true},
          {"name":"price","path":"payload.price","type":"FIXED_DECIMAL","scale":2,"required":true}
        ]
      }
    }
  }
}
EOF

pipeline_response="$(curl --silent --show-error --fail --request POST \
  --header 'Content-Type: application/json' --data-binary "@$workdir/pipeline.json" \
  http://localhost:8080/api/v1/pipelines)"
pipeline_id="$(printf '%s' "$pipeline_response" | sed -nE 's/^\{"id":"([^"]+)".*/\1/p')"
[[ -n "$pipeline_id" ]]

run_response="$(curl --silent --show-error --fail --request POST \
  --header 'Content-Type: application/json' --data '{}' \
  "http://localhost:8080/api/v1/pipelines/$pipeline_id/runs")"
run_id="$(printf '%s' "$run_response" | sed -nE 's/^\{"runId":"([^"]+)".*/\1/p')"
[[ -n "$run_id" ]]
run_url="http://localhost:8080/api/v1/pipelines/$pipeline_id/runs/$run_id"

state=""
for _ in {1..120}; do
  run_response="$(curl --silent --show-error --fail "$run_url")"
  state="$(printf '%s' "$run_response" | sed -nE 's/.*"state":"([A-Z]+)".*/\1/p')"
  [[ "$state" == "COMPLETED" || "$state" == "STOPPED" || "$state" == "FAILED" ]] && break
  sleep 1
done
[[ "$state" == "COMPLETED" ]]

monitoring="$(curl --silent --show-error --fail "$run_url/monitoring")"
printf '%s' "$monitoring" | grep -q '"outputAvailable":true'
printf '%s' "$monitoring" | grep -q '"rawCaptureAvailable":true'
curl --silent --show-error --fail "$run_url/output" --output "$workdir/output.parquet"
[[ "$(od -An -c -N4 "$workdir/output.parquet" | tr -d ' ')" == "PAR1" ]]
curl --silent --show-error --fail "$run_url/raw-capture" --output "$workdir/raw.capture"
cmp schemas/examples/pipeline-aapl-input.jsonl "$workdir/raw.capture"

metrics="$(curl --silent --show-error --fail http://localhost:8080/actuator/prometheus)"
for metric in \
  streamforge_pipeline_runs_total \
  streamforge_pipeline_records_total \
  streamforge_pipeline_processing_nanoseconds_total \
  streamforge_pipeline_sequence_anomalies_total \
  streamforge_pipeline_active_runs; do
  printf '%s' "$metrics" | grep -q "$metric"
done

docker compose -p "$project" restart control-plane
for _ in {1..60}; do
  curl --silent --fail http://localhost:8080/actuator/health/readiness >/dev/null && break
  sleep 1
done
curl --silent --show-error --fail "$run_url" | grep -q '"state":"COMPLETED"'
curl --silent --show-error --fail "$run_url/output" --output "$workdir/restored-output.parquet"
cmp "$workdir/output.parquet" "$workdir/restored-output.parquet"

printf 'V1 Docker acceptance passed for pipeline %s run %s.\n' "$pipeline_id" "$run_id"
