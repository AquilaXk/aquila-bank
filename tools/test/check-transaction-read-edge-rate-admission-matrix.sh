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
edge_rate_rps	source_mode	hot_burst	archive_burst	backend_admission_max	hikari_max	arrival16_delayed_rate	vu16_429_rate	burst48_429_rate	burst64_429_rate	backend_429_rate	backend_rejected_count	backend_pending	backend_cpu_percent	hikari_warning_count
64	single-source	8	8	7	8	0.18	0.2995	0.28	0.42	0.0014	29	0	44.5	0
96	multi-source	12	12	8	10	0.18	0.0450	0.0950	0.2088	0.0002	0	0	58.0	0
128	multi-source	16	16	8	10	0.05	0.0200	0.0400	0.0900	0.0002	0	0	62.0	0
TSV

echo "[transaction-read-edge-rate-matrix] print plan"
plan="$(
  EDGE_RATE_MATRIX_NAME=edge-rate-check \
  EDGE_RATE_MATRIX_INPUT_TSV="${matrix_tsv}" \
  EDGE_RATE_MATRIX_OUTPUT_DIR="${output_dir}" \
  EDGE_RATE_MATRIX_REQUIRED_CANDIDATE_RATES=96,128 \
    "${runner}" --print-plan
)"
grep -F "name=edge-rate-check" <<<"${plan}" >/dev/null
grep -F "input_tsv=${matrix_tsv}" <<<"${plan}" >/dev/null
grep -F "candidate_rates=64,96,128" <<<"${plan}" >/dev/null
grep -F "required_candidate_rates=96,128" <<<"${plan}" >/dev/null
grep -F "max_burst48_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "max_burst64_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "max_backend_429_rate=0.0005" <<<"${plan}" >/dev/null
grep -F "max_backend_rejected=0" <<<"${plan}" >/dev/null
grep -F "max_backend_cpu_percent=70" <<<"${plan}" >/dev/null
grep -F "recommended_policy=highest-pass-lowest-edge-reject" <<<"${plan}" >/dev/null

echo "[transaction-read-edge-rate-matrix] report"
output="$(
  EDGE_RATE_MATRIX_NAME=edge-rate-check \
  EDGE_RATE_MATRIX_INPUT_TSV="${matrix_tsv}" \
  EDGE_RATE_MATRIX_OUTPUT_DIR="${output_dir}" \
  EDGE_RATE_MATRIX_REQUIRED_CANDIDATE_RATES=96,128 \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/edge-rate-check-rate-admission-matrix.tsv"
test "${report_md}" = "${output_dir}/edge-rate-check-rate-admission-matrix.md"
grep -F $'edge_rate_rps\tstatus\tsource_mode\thot_burst\tarchive_burst\tbackend_admission_max\thikari_max\tarrival16_delayed_rate\tvu16_429_rate\tburst48_429_rate\tburst64_429_rate\tbackend_429_rate\tbackend_rejected_count\tbackend_pending\tbackend_cpu_percent\thikari_warning_count' "${summary_tsv}" >/dev/null
grep -F $'64\tfail\tsingle-source\t8\t8\t7\t8\t0.18\t0.2995\t0.28\t0.42\t0.0014\t29\t0\t44.5\t0' "${summary_tsv}" >/dev/null
grep -F $'96\tfail\tmulti-source\t12\t12\t8\t10\t0.18\t0.0450\t0.0950\t0.2088\t0.0002\t0\t0\t58.0\t0' "${summary_tsv}" >/dev/null
grep -F $'128\tpass\tmulti-source\t16\t16\t8\t10\t0.05\t0.0200\t0.0400\t0.0900\t0.0002\t0\t0\t62.0\t0' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "recommended edge rate: 128r/s" "${report_md}" >/dev/null
grep -F "required candidate rates: 96,128" "${report_md}" >/dev/null
grep -F "backend 429 budget: < 0.0005" "${report_md}" >/dev/null
grep -F "backend rejected budget: <= 0" "${report_md}" >/dev/null
grep -F "shared NAT note" "${report_md}" >/dev/null

echo "[transaction-read-edge-rate-matrix] fail report"
awk 'NR == 1 || $1 != 128' "${matrix_tsv}" >"${matrix_tsv}.fail"
if EDGE_RATE_MATRIX_NAME=edge-rate-fail \
  EDGE_RATE_MATRIX_INPUT_TSV="${matrix_tsv}.fail" \
  EDGE_RATE_MATRIX_OUTPUT_DIR="${output_dir}" \
  EDGE_RATE_MATRIX_REQUIRED_CANDIDATE_RATES=96,128 \
    "${runner}" >/dev/null 2>&1; then
  echo "edge rate matrix unexpectedly passed without required 128r/s candidate" >&2
  exit 1
fi
