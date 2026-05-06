#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-evidence-completeness-gate.sh"

echo "[transaction-read-evidence-completeness] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

evidence_dir="${temp_dir}/evidence"
output_dir="${temp_dir}/output"
mkdir -p "${evidence_dir}" "${output_dir}"

touch \
  "${evidence_dir}/k6-summary.json" \
  "${evidence_dir}/nginx-access-aggregate.tsv"

echo "[transaction-read-evidence-completeness] missing artifacts fail"
if EVIDENCE_COMPLETENESS_NAME=evidence-missing-check \
  EVIDENCE_COMPLETENESS_INPUT_DIR="${evidence_dir}" \
  EVIDENCE_COMPLETENESS_OUTPUT_DIR="${output_dir}" \
  EVIDENCE_COMPLETENESS_REQUIRED_FILES="k6-summary.json,nginx-access-aggregate.tsv,source-429.tsv,hikari.log,postgres-wait.tsv" \
    "${runner}" >/dev/null 2>&1; then
  echo "evidence completeness gate unexpectedly passed missing files" >&2
  exit 1
fi

EVIDENCE_COMPLETENESS_NAME=evidence-missing-check \
EVIDENCE_COMPLETENESS_INPUT_DIR="${evidence_dir}" \
EVIDENCE_COMPLETENESS_OUTPUT_DIR="${output_dir}" \
EVIDENCE_COMPLETENESS_REQUIRED_FILES="k6-summary.json,nginx-access-aggregate.tsv,source-429.tsv,hikari.log,postgres-wait.tsv" \
  "${runner}" >/dev/null 2>&1 || true

summary_tsv="${output_dir}/evidence-missing-check-evidence-completeness.tsv"
report_md="${output_dir}/evidence-missing-check-evidence-completeness.md"
grep -F $'artifact\tstatus\tpath\treason' "${summary_tsv}" >/dev/null
grep -F $'k6-summary.json\tpresent\t' "${summary_tsv}" >/dev/null
grep -F $'source-429.tsv\tmissing\t' "${summary_tsv}" >/dev/null
grep -F "gate_status=fail" "${report_md}" >/dev/null
grep -F "source-429.tsv" "${report_md}" >/dev/null
grep -F "postgres-wait.tsv" "${report_md}" >/dev/null

echo "[transaction-read-evidence-completeness] complete artifacts pass"
touch \
  "${evidence_dir}/source-429.tsv" \
  "${evidence_dir}/hikari.log" \
  "${evidence_dir}/postgres-wait.tsv"

output="$(
  EVIDENCE_COMPLETENESS_NAME=evidence-complete-check \
  EVIDENCE_COMPLETENESS_INPUT_DIR="${evidence_dir}" \
  EVIDENCE_COMPLETENESS_OUTPUT_DIR="${output_dir}" \
  EVIDENCE_COMPLETENESS_REQUIRED_FILES="k6-summary.json,nginx-access-aggregate.tsv,source-429.tsv,hikari.log,postgres-wait.tsv" \
    "${runner}"
)"
report_path="$(tail -1 <<<"${output}")"
test "${report_path}" = "${output_dir}/evidence-complete-check-evidence-completeness.md"
grep -F "gate_status=pass" "${report_path}" >/dev/null
