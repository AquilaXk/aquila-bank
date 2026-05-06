#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-evidence-completeness-gate.sh [--print-plan]

Environment:
  EVIDENCE_COMPLETENESS_NAME            default transaction-read-evidence-completeness-<timestamp>
  EVIDENCE_COMPLETENESS_INPUT_DIR       required evidence artifact directory
  EVIDENCE_COMPLETENESS_OUTPUT_DIR      default build/reports/k6/<name>
  EVIDENCE_COMPLETENESS_REQUIRED_FILES  comma-separated artifact file names
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

name="${EVIDENCE_COMPLETENESS_NAME:-transaction-read-evidence-completeness-$(date +%Y-%m-%d-%H%M%S)}"
input_dir="${EVIDENCE_COMPLETENESS_INPUT_DIR:-}"
output_dir="${EVIDENCE_COMPLETENESS_OUTPUT_DIR:-build/reports/k6/${name}}"
required_files_csv="${EVIDENCE_COMPLETENESS_REQUIRED_FILES:-k6-summary.json,nginx-access-aggregate.tsv,source-429.tsv,hikari.log,postgres-wait.tsv}"
summary_tsv="${output_dir}/${name}-evidence-completeness.tsv"
report_md="${output_dir}/${name}-evidence-completeness.md"

print_plan() {
  echo "[transaction-read-evidence-completeness] name=${name}"
  echo "[transaction-read-evidence-completeness] input_dir=${input_dir:-missing}"
  echo "[transaction-read-evidence-completeness] output_dir=${output_dir}"
  echo "[transaction-read-evidence-completeness] required_files=${required_files_csv}"
  echo "[transaction-read-evidence-completeness] summary_tsv=${summary_tsv}"
  echo "[transaction-read-evidence-completeness] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ -z "${input_dir}" || ! -d "${input_dir}" ]]; then
  echo "EVIDENCE_COMPLETENESS_INPUT_DIR must be an existing directory: ${input_dir:-missing}" >&2
  exit 1
fi
if [[ -z "${required_files_csv}" ]]; then
  echo "EVIDENCE_COMPLETENESS_REQUIRED_FILES must not be empty" >&2
  exit 1
fi

mkdir -p "${output_dir}"
IFS=',' read -r -a required_files <<<"${required_files_csv}"

missing_count=0
{
  printf "artifact\tstatus\tpath\treason\n"
  for artifact in "${required_files[@]}"; do
    if [[ -z "${artifact}" ]]; then
      continue
    fi
    artifact_path="${input_dir%/}/${artifact}"
    if [[ -e "${artifact_path}" ]]; then
      printf "%s\tpresent\t%s\tok\n" "${artifact}" "${artifact_path}"
    else
      printf "%s\tmissing\t%s\tmissing-required-artifact\n" "${artifact}" "${artifact_path}"
      missing_count=$((missing_count + 1))
    fi
  done
} >"${summary_tsv}"

status="pass"
if ((missing_count > 0)); then
  status="fail"
fi

{
  echo "# Transaction Read Evidence Completeness"
  echo
  echo "## Summary"
  echo
  echo "- gate_status=${status}"
  echo "- input_dir=${input_dir}"
  echo "- missing_count=${missing_count}"
  echo
  echo "## Artifacts"
  echo
  echo "| artifact | status | reason |"
  echo "| --- | --- | --- |"
  awk -F '\t' 'NR > 1 { printf "| %s | %s | %s |\n", $1, $2, $4 }' "${summary_tsv}"
} >"${report_md}"

echo "${report_md}"
if ((missing_count > 0)); then
  echo "transaction read evidence completeness failed: ${summary_tsv}" >&2
  exit 1
fi
