#!/usr/bin/env sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
results_directory="$repository_root/tests/performance/results"
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
result_file="$results_directory/stp-jmh-$timestamp.json"

mkdir -p "$results_directory"
"$repository_root/backend/mvnw" -f "$repository_root/backend/pom.xml" -pl stp-benchmarks -am package
exec java -jar "$repository_root/backend/stp-benchmarks/target/stp-benchmarks-0.1.0-benchmarks.jar" \
  -rf json \
  -rff "$result_file" \
  "$@"
