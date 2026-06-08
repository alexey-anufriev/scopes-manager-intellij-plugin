#!/usr/bin/env bash
set -euo pipefail

if [[ $# -eq 0 ]]; then
  echo "Usage: run-gradle-isolated <gradle-task> [gradle args...]" >&2
  exit 2
fi

if [[ ! -d /source ]]; then
  echo "Expected repository mounted read-only at /source." >&2
  exit 2
fi

export HOME=/home/tester
export XDG_CONFIG_HOME="${HOME}/.config"
export XDG_CACHE_HOME="${HOME}/.cache"
export XDG_DATA_HOME="${HOME}/.local/share"
export XDG_STATE_HOME="${HOME}/.local/state"
export GRADLE_USER_HOME="${HOME}/.gradle"
export GDK_BACKEND=x11
export XDG_SESSION_TYPE=x11
export WAYLAND_DISPLAY=

mkdir -p "${XDG_CONFIG_HOME}" "${XDG_CACHE_HOME}" "${XDG_DATA_HOME}" "${XDG_STATE_HOME}" "${GRADLE_USER_HOME}" /work

rsync -a --delete \
  --exclude='/.gradle/' \
  --exclude='/.idea/' \
  --exclude='/.intellijPlatform/' \
  --exclude='/.kotlin/' \
  --exclude='/allure-results/' \
  --exclude='/build/' \
  --exclude='/out/' \
  /source/ /work/

cd /work

timeout_value="${INTEGRATION_TEST_TIMEOUT:-30m}"
artifacts_dir="/artifacts"

copy_test_artifacts() {
  if [[ ! -d "${artifacts_dir}" ]]; then
    return
  fi

  echo "[isolated-test] Copying test artifacts to ${artifacts_dir}"
  mkdir -p "${artifacts_dir}"

  for path in build/test-results build/reports/tests out/ide-tests/tests; do
    if [[ -e "${path}" ]]; then
      target="${artifacts_dir}/${path}"
      rm -rf "${target}"
      mkdir -p "$(dirname "${target}")"
      cp -a "${path}" "${target}"
      echo "[isolated-test] Copied ${path}"
    else
      echo "[isolated-test] No ${path} found"
    fi
  done
}

set +e
timeout --foreground "${timeout_value}" xvfb-run -a ./gradlew "$@" --stacktrace --info --no-daemon --rerun-tasks
status=$?
set -e

copy_test_artifacts
exit "${status}"
