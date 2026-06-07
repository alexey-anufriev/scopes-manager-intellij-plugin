#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage: scripts/run-isolated-integration-test.sh <gradle-task> [gradle args...]

Runs a Gradle integration test task in a disposable container.

The repository is mounted read-only, copied into an internal /work directory,
and host IDE state such as JetBrains Toolbox settings, license files, build outputs,
and IDE test caches are not mounted into the test run.

Environment:
  INTEGRATION_TEST_IMAGE     Image tag to build/use. Default: scopes-manager-integration-tests:latest
  INTEGRATION_TEST_TIMEOUT   Timeout passed to timeout(1). Default: 30m
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -eq 0 ]]; then
  usage
  exit 2
fi

repo_root="$(git rev-parse --show-toplevel)"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required but was not found on PATH." >&2
  exit 1
fi

image="${INTEGRATION_TEST_IMAGE:-scopes-manager-integration-tests:latest}"
timeout_value="${INTEGRATION_TEST_TIMEOUT:-30m}"

docker build \
  -f "${repo_root}/docker/integration-tests.Dockerfile" \
  -t "${image}" \
  "${repo_root}"

exec docker run \
  --rm \
  --init \
  -e "INTEGRATION_TEST_TIMEOUT=${timeout_value}" \
  -v "${repo_root}:/source:ro" \
  "${image}" \
  "$@"
