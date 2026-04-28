#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-100m-offhost-capacity-baseline-report.sh"

echo "[transaction-100m-offhost-baseline] shell syntax"
bash -n "${script}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

env_file="${temp_dir}/offhost-capacity.env"
summary="${temp_dir}/capacity-summary.tsv"
context="${temp_dir}/capacity-run-context.env"
prereq="${temp_dir}/capacity-prerequisite-failure.env"
cat >"${env_file}" <<'ENV'
CAPACITY_K6_DOCKER_CONTEXT=capacity-k6-remote
CAPACITY_K6_REMOTE_BASE_URL=http://192.0.2.20:18080
CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.20:9090/api/v1/write
CAPACITY_K6_REMOTE_WORKDIR=/srv/aquila-bank
ENV
printf "phase\tprofile\tstatus\tadmission\tvus\tbackend_cpus\tbackend_memory\tpostgres_cpus\tpostgres_memory\tdb_pool\toverload_mode\tduration\thttp_failed_rate\thttp_reqs\ttransaction_429_rate\thot_first_p95_ms\thot_cursor_p95_ms\tcold_first_p95_ms\tcold_cursor_p95_ms\tbackend_cpu_percent\tpostgres_cpu_percent\thikari_active\thikari_pending\thikari_max\tlog_path\tsummary_json\nsingle-host\tsingle-host-default\t0\t3\t8\t0.40\t512m\t0.60\t384m\t4\ttrue\t1m\t0\t1200\t0.010000\t210\t220\t610\t640\t82.50\t41.00\t3\t0\t4\tcapacity.log\tcapacity-summary.json\nlong-soak\tlong-soak-high-traffic\t0\t8\t8\t0.80\t640m\t0.60\t384m\t6\tfalse\t30m\t0\t12000\t0.000000\t230\t240\t650\t680\t88.25\t45.00\t5\t0\t6\tsoak.log\tsoak-summary.json\n" >"${summary}"
cat >"${context}" <<'ENV'
CAPACITY_RUN_PURPOSE=capacity
CAPACITY_NAME=offhost-baseline-check
CAPACITY_GENERATOR_MODE=docker-context
ENV
cat >"${prereq}" <<'ENV'
CAPACITY_PREREQUISITE_STATUS=missing
CAPACITY_PREREQUISITE_FAILURE_REASON=missing
CAPACITY_PREREQUISITE_MISSING_VARS=missing
ENV

echo "[transaction-100m-offhost-baseline] plan"
plan="$(
  OFFHOST_BASELINE_NAME=offhost-baseline-check \
  OFFHOST_BASELINE_OUTPUT_DIR="${temp_dir}" \
  OFFHOST_BASELINE_ENV_FILE="${env_file}" \
  OFFHOST_BASELINE_CAPACITY_SUMMARY_TSV="${summary}" \
  OFFHOST_BASELINE_CAPACITY_RUN_CONTEXT_ENV="${context}" \
  OFFHOST_BASELINE_CAPACITY_PREREQUISITE_ENV="${prereq}" \
  OFFHOST_BASELINE_RUN_CAPACITY=false \
  OFFHOST_BASELINE_DOCTOR_CONNECTIVITY=false \
    "${script}" --print-plan
)"
grep -F "name=offhost-baseline-check" <<<"${plan}" >/dev/null
grep -F "output=${temp_dir}/offhost-baseline-check.md" <<<"${plan}" >/dev/null
grep -F "env_file=${env_file}" <<<"${plan}" >/dev/null
grep -F "run_capacity=false" <<<"${plan}" >/dev/null
grep -F "doctor_connectivity=false" <<<"${plan}" >/dev/null
grep -F "capacity_summary=${summary}" <<<"${plan}" >/dev/null
grep -F "capacity_run_context=${context}" <<<"${plan}" >/dev/null
grep -F "capacity_prerequisite=${prereq}" <<<"${plan}" >/dev/null

echo "[transaction-100m-offhost-baseline] dry-run report"
output="$(
  OFFHOST_BASELINE_NAME=offhost-baseline-check \
  OFFHOST_BASELINE_OUTPUT_DIR="${temp_dir}" \
  OFFHOST_BASELINE_ENV_FILE="${env_file}" \
  OFFHOST_BASELINE_CAPACITY_SUMMARY_TSV="${summary}" \
  OFFHOST_BASELINE_CAPACITY_RUN_CONTEXT_ENV="${context}" \
  OFFHOST_BASELINE_CAPACITY_PREREQUISITE_ENV="${prereq}" \
  OFFHOST_BASELINE_RUN_CAPACITY=false \
  OFFHOST_BASELINE_DOCTOR_CONNECTIVITY=false \
    "${script}" --dry-run
)"
output="$(tail -1 <<<"${output}")"
test "${output}" = "${temp_dir}/offhost-baseline-check.md"
grep -F "| capacity | pass | 82.50 | 0.010000 | 0 | ${summary} |" "${output}" >/dev/null
grep -F "| soak | pass | 88.25 | 0.000000 | 0 | ${summary} |" "${output}" >/dev/null
grep -F "| prerequisite | missing | n/a | n/a | n/a | reason=missing missing=missing |" "${output}" >/dev/null

echo "[transaction-100m-offhost-baseline] invalid input fails"
if OFFHOST_BASELINE_RUN_CAPACITY=maybe "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad OFFHOST_BASELINE_RUN_CAPACITY unexpectedly passed" >&2
  exit 1
fi
if OFFHOST_BASELINE_LONG_SOAK_DURATION=0m "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad OFFHOST_BASELINE_LONG_SOAK_DURATION unexpectedly passed" >&2
  exit 1
fi

echo "[transaction-100m-offhost-baseline] runner contract"
grep -F "run-offhost-capacity-env-doctor.sh" "${script}" >/dev/null
grep -F "run-transaction-100m-capacity-gates.sh" "${script}" >/dev/null
grep -F "OFFHOST_BASELINE_CAPACITY_SUMMARY_TSV" "${script}" >/dev/null
grep -F "OFFHOST_BASELINE_CAPACITY_PREREQUISITE_ENV" "${script}" >/dev/null
grep -F "OFFHOST_BASELINE_DOCTOR_CONNECTIVITY" "${script}" >/dev/null
