#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-edge-rate-admission-matrix.sh"

echo "[transaction-read-edge-rate-matrix] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

matrix_tsv="${temp_dir}/matrix.tsv"
output_dir="${temp_dir}/output"

cat >"${matrix_tsv}" <<'TSV'
edge_rate_rps	hot_burst	archive_burst	backend_admission_max	hikari_max	arrival16_delayed_rate	vu16_429_rate	burst48_429_rate	backend_pending	backend_cpu_percent	hikari_warning_count
56	8	8	6	6	0.22	0.14	0.42	0	38.2	0
64	8	8	7	8	0.18	0.08	0.28	0	44.5	0
72	8	8	8	10	0.15	0.07	0.25	1	61.0	1
TSV

echo "[transaction-read-edge-rate-matrix] print plan"
plan="$(
  EDGE_RATE_MATRIX_NAME=edge-rate-check \
  EDGE_RATE_MATRIX_INPUT_TSV="${matrix_tsv}" \
  EDGE_RATE_MATRIX_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=edge-rate-check" <<<"${plan}" >/dev/null
grep -F "input_tsv=${matrix_tsv}" <<<"${plan}" >/dev/null
grep -F "candidate_rates=56,64,72" <<<"${plan}" >/dev/null
grep -F "recommended_policy=highest-pass-lowest-edge-reject" <<<"${plan}" >/dev/null

echo "[transaction-read-edge-rate-matrix] report"
output="$(
  EDGE_RATE_MATRIX_NAME=edge-rate-check \
  EDGE_RATE_MATRIX_INPUT_TSV="${matrix_tsv}" \
  EDGE_RATE_MATRIX_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/edge-rate-check-rate-admission-matrix.tsv"
test "${report_md}" = "${output_dir}/edge-rate-check-rate-admission-matrix.md"
grep -F $'edge_rate_rps\tstatus\thot_burst\tarchive_burst\tbackend_admission_max\thikari_max\tarrival16_delayed_rate\tvu16_429_rate\tburst48_429_rate\tbackend_pending\tbackend_cpu_percent\thikari_warning_count' "${summary_tsv}" >/dev/null
grep -F $'56\tfail\t8\t8\t6\t6\t0.22\t0.14\t0.42\t0\t38.2\t0' "${summary_tsv}" >/dev/null
grep -F $'64\tpass\t8\t8\t7\t8\t0.18\t0.08\t0.28\t0\t44.5\t0' "${summary_tsv}" >/dev/null
grep -F $'72\tfail\t8\t8\t8\t10\t0.15\t0.07\t0.25\t1\t61.0\t1' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "recommended edge rate: 64r/s" "${report_md}" >/dev/null
grep -F "shared NAT note" "${report_md}" >/dev/null

echo "[transaction-read-edge-rate-matrix] fail report"
awk 'NR == 1 || $1 != 64' "${matrix_tsv}" >"${matrix_tsv}.fail"
if EDGE_RATE_MATRIX_NAME=edge-rate-fail \
  EDGE_RATE_MATRIX_INPUT_TSV="${matrix_tsv}.fail" \
  EDGE_RATE_MATRIX_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "edge rate matrix unexpectedly passed without a candidate" >&2
  exit 1
fi
