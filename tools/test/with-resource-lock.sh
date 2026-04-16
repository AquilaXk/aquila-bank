#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -lt 2 ]]; then
  echo "usage: $0 <resource-name> <command> [args...]" >&2
  exit 1
fi

resource_name="$1"
shift

lock_root="${TMPDIR:-/tmp}/aquila-bank-locks"
lock_dir="${lock_root}/${resource_name}.lock"
mkdir -p "${lock_root}"

wait_notice_printed="false"

cleanup() {
  rmdir "${lock_dir}" 2>/dev/null || true
}

while ! mkdir "${lock_dir}" 2>/dev/null; do
  if [[ "${wait_notice_printed}" != "true" ]]; then
    echo "[lock] ${resource_name} is busy. Waiting for another local run to finish..." >&2
    wait_notice_printed="true"
  fi
  sleep 0.2
done

trap cleanup EXIT INT TERM

"$@"
