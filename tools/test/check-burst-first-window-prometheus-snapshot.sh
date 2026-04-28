#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-burst-first-window-prometheus-snapshot.sh"

echo "[burst-first-window-prometheus] shell syntax"
bash -n "${script}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

echo "[burst-first-window-prometheus] plan"
plan="$(
  BURST_WINDOW_SNAPSHOT_NAME=burst-window-check \
  BURST_WINDOW_RUN_ID=transaction-100m-burst-window-check \
  BURST_WINDOW_PROMETHEUS_URL=http://127.0.0.1:9090 \
  BURST_WINDOW_SECONDS=3 \
  BURST_WINDOW_OUTPUT_DIR="${temp_dir}" \
    "${script}" --print-plan
)"
grep -F "name=burst-window-check" <<<"${plan}" >/dev/null
grep -F "run_id=transaction-100m-burst-window-check" <<<"${plan}" >/dev/null
grep -F "prometheus_url=http://127.0.0.1:9090" <<<"${plan}" >/dev/null
grep -F "window_seconds=3" <<<"${plan}" >/dev/null
grep -F "snapshot_tsv=${temp_dir}/burst-window-check-prometheus-first-3s.tsv" <<<"${plan}" >/dev/null

echo "[burst-first-window-prometheus] dry-run"
dry_run="$(
  BURST_WINDOW_SNAPSHOT_NAME=burst-window-check \
  BURST_WINDOW_RUN_ID=transaction-100m-burst-window-check \
  BURST_WINDOW_PROMETHEUS_URL=http://127.0.0.1:9090 \
  BURST_WINDOW_SECONDS=3 \
  BURST_WINDOW_OUTPUT_DIR="${temp_dir}" \
    "${script}" --dry-run
)"
grep -F "max_over_time(k6_vus" <<<"${dry_run}" >/dev/null
grep -F "run_id=\"transaction-100m-burst-window-check\"" <<<"${dry_run}" >/dev/null
grep -F "db.hikari.pending.max" <<<"${dry_run}" >/dev/null

echo "[burst-first-window-prometheus] invalid input fails"
if BURST_WINDOW_SECONDS=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "zero window unexpectedly passed" >&2
  exit 1
fi
if BURST_WINDOW_PROMETHEUS_URL=not-a-url "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad prometheus url unexpectedly passed" >&2
  exit 1
fi

echo "[burst-first-window-prometheus] runner contract"
grep -F "k6.vus.max" "${script}" >/dev/null
grep -F "k6.vus.active.max" "${script}" >/dev/null
grep -F "backend.cpu.max.percent" "${script}" >/dev/null
grep -F "postgres.cpu.max.percent" "${script}" >/dev/null
grep -F "db.hikari.pending.max" "${script}" >/dev/null
grep -F "/api/v1/query" "${script}" >/dev/null
