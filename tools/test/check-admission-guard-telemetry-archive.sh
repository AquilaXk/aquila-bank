#!/usr/bin/env bash
set -euo pipefail

script="tools/test/archive-admission-guard-telemetry-snapshot.sh"

echo "[admission-telemetry-archive] shell syntax"
bash -n "${script}"

echo "[admission-telemetry-archive] archive sample"
temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
summary_tsv="${temp_dir}/http-admission-summary.tsv"
raw_tsv="${temp_dir}/http-admission-raw.tsv"
{
  printf "requests\tsuccess_count\trejected_count\tfailed_count\tfailed_rate\tretry_after_count\traw_path\n"
  printf "16\t8\t8\t0\t0.000000\t8\t%s\n" "${raw_tsv}"
} >"${summary_tsv}"
{
  printf "request_id\tstatus\tduration_seconds\tretry_after\n"
  printf "1\t429\t0.012\t1\n"
} >"${raw_tsv}"

output="$(
  ADMISSION_TELEMETRY_RESULT_NAME=admission-telemetry-check \
    "${script}" "${summary_tsv}" "${raw_tsv}"
)"
test "${output}" = "docs/performance-results/admission-telemetry-check.md"
grep -F "# admission-telemetry-check" "${output}" >/dev/null
grep -F "sourceSummary: ${summary_tsv}" "${output}" >/dev/null
grep -F "sourceRaw: ${raw_tsv}" "${output}" >/dev/null
grep -F $'16\t8\t8\t0\t0.000000\t8' "${output}" >/dev/null
grep -F "429" "${output}" >/dev/null
rm -f "${output}"

echo "[admission-telemetry-archive] contract"
grep -F "ADMISSION_TELEMETRY_RESULT_NAME" "${script}" >/dev/null
grep -F "docs/performance-results" "${script}" >/dev/null
grep -F "sourceSummary" "${script}" >/dev/null
grep -F "Admission Guard Telemetry" "${script}" >/dev/null
grep -F "secret" "${script}" >/dev/null
grep -F "archive-admission-guard-telemetry-snapshot.sh" docs/performance-results/README.md >/dev/null

echo "[admission-telemetry-archive] invalid input fails"
if "${script}" "${temp_dir}/missing.tsv" >/dev/null 2>&1; then
  echo "missing summary unexpectedly succeeded" >&2
  exit 1
fi
if "${script}" "${summary_tsv}" "${temp_dir}/missing-raw.tsv" >/dev/null 2>&1; then
  echo "missing raw unexpectedly succeeded" >&2
  exit 1
fi
