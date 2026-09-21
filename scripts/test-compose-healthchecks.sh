#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
configuration="$(docker compose -f "$root/compose.yaml" config --format json)"

if [[ "$configuration" != *'http://127.0.0.1:5173/'* \
  || "$configuration" == *'http://localhost:5173/'* ]]; then
  printf '%s\n' \
    'Dashboard healthcheck must use the IPv4 loopback address; Alpine localhost resolves to IPv6.' \
    >&2
  exit 1
fi
