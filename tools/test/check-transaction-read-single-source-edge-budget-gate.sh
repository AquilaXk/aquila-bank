#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-single-source-edge-budget-gate.sh"

echo "[transaction-read-single-source-budget] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

budget_tsv="${temp_dir}/single-source-budget.tsv"
output_dir="${temp_dir}/output"

cat >"${budget_tsv}" <<'TSV'
run	source_mode	workload_shape	edge_429_rate	backend_429_rate	backend_rejected_count	upstream_502_count	client_499_count	hikari_warning_count	accepted_p95_ms
vu16-soak-2m	single-source	fixed	0.2995	0	0	0	0	0	93.3
weighted-vu16-soak-2m	single-source	weighted-random	0.2763	0.0014	29	0	0	0	93.5
weighted-vu16-multisource-10m	multi-source	weighted-random	0.0800	0.0002	0	0	0	0	145.0
TSV

echo "[transaction-read-single-source-budget] print plan"
plan="$(
  SINGLE_SOURCE_EDGE_BUDGET_NAME=single-source-budget-check \
  SINGLE_SOURCE_EDGE_BUDGET_INPUT_TSV="${budget_tsv}" \
  SINGLE_SOURCE_EDGE_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=single-source-budget-check" <<<"${plan}" >/dev/null
grep -F "input_tsv=${budget_tsv}" <<<"${plan}" >/dev/null
grep -F "defensive_single_source_edge_429_rate=0.30" <<<"${plan}" >/dev/null
grep -F "operating_edge_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "operating_backend_429_rate=0.0005" <<<"${plan}" >/dev/null
grep -F "decision=single-source-defensive-lab-only" <<<"${plan}" >/dev/null

echo "[transaction-read-single-source-budget] report"
output="$(
  SINGLE_SOURCE_EDGE_BUDGET_NAME=single-source-budget-check \
  SINGLE_SOURCE_EDGE_BUDGET_INPUT_TSV="${budget_tsv}" \
  SINGLE_SOURCE_EDGE_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/single-source-budget-check-single-source-edge-budget.tsv"
test "${report_md}" = "${output_dir}/single-source-budget-check-single-source-edge-budget.md"
grep -F $'run\tstatus\tbudget_class\tsource_mode\tworkload_shape\tedge_429_rate\tedge_429_budget\tbackend_429_rate\tbackend_429_budget\tbackend_rejected_count\toperating_candidate\treason' "${summary_tsv}" >/dev/null
grep -F $'vu16-soak-2m\tpass\tdefensive-lab\tsingle-source\tfixed\t0.2995\t0.30\t0\t0.005\t0\tno\twithin-defensive-single-source-budget' "${summary_tsv}" >/dev/null
grep -F $'weighted-vu16-soak-2m\tpass\tdefensive-lab\tsingle-source\tweighted-random\t0.2763\t0.30\t0.0014\t0.005\t29\tno\twithin-defensive-single-source-budget' "${summary_tsv}" >/dev/null
grep -F $'weighted-vu16-multisource-10m\tpass\toperating-candidate\tmulti-source\tweighted-random\t0.0800\t0.10\t0.0002\t0.0005\t0\tyes\twithin-operating-budget' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "single-source decision: defensive-lab-only" "${report_md}" >/dev/null
grep -F "operating edge 429 target: < 0.10" "${report_md}" >/dev/null
grep -F "operating candidates: 1" "${report_md}" >/dev/null

echo "[transaction-read-single-source-budget] fail report"
cat >"${budget_tsv}.fail" <<'TSV'
run	source_mode	workload_shape	edge_429_rate	backend_429_rate	backend_rejected_count	upstream_502_count	client_499_count	hikari_warning_count	accepted_p95_ms
vu16-soak-2m	single-source	fixed	0.3100	0	0	0	0	0	93.3
TSV
if SINGLE_SOURCE_EDGE_BUDGET_NAME=single-source-budget-fail \
  SINGLE_SOURCE_EDGE_BUDGET_INPUT_TSV="${budget_tsv}.fail" \
  SINGLE_SOURCE_EDGE_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "single-source edge budget unexpectedly passed edge 429 over defensive budget" >&2
  exit 1
fi

echo "[transaction-read-single-source-budget] hard zero report"
cat >"${budget_tsv}.hard-zero-fail" <<'TSV'
run	source_mode	workload_shape	edge_429_rate	backend_429_rate	backend_rejected_count	upstream_502_count	client_499_count	hikari_warning_count	accepted_p95_ms
arrival-8	single-source	fixed	0.0000	0	0	0	1	0	88.2
TSV
if SINGLE_SOURCE_EDGE_BUDGET_NAME=single-source-budget-hard-zero-fail \
  SINGLE_SOURCE_EDGE_BUDGET_INPUT_TSV="${budget_tsv}.hard-zero-fail" \
  SINGLE_SOURCE_EDGE_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "single-source edge budget unexpectedly passed 499 hard-zero regression" >&2
  exit 1
fi
