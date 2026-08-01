#!/usr/bin/env sh

set -eu

script_dir=$(CDPATH= cd -P "$(dirname "$0")" && pwd)
repository_root=$(CDPATH= cd -P "$script_dir/.." && pwd)
scope=${1:-all}

case "$scope" in
  all | backend | web)
    ;;
  *)
    printf '%s\n' "Usage: $0 [all|backend|web]" >&2
    exit 2
    ;;
esac

cd "$repository_root"

run_backend_check() {
  ./backend/mvnw -f backend/pom.xml verify
}

run_web_check() {
  npm --prefix web-dashboard ci
  npm --prefix web-dashboard run format:check
  npm --prefix web-dashboard run lint
  npm --prefix web-dashboard run test
  npm --prefix web-dashboard run build
}

case "$scope" in
  all)
    run_backend_check
    run_web_check
    ;;
  backend)
    run_backend_check
    ;;
  web)
    run_web_check
    ;;
esac
