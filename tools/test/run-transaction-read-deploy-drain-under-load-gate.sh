#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-deploy-drain-under-load-gate.sh [--print-plan]

Environment:
  DEPLOY_DRAIN_GATE_NAME        default transaction-read-deploy-drain-under-load-<timestamp>
  DEPLOY_DRAIN_GATE_INPUT_TSV   required OCI evidence manifest with deploy-drain row
  DEPLOY_DRAIN_GATE_OUTPUT_DIR  default build/reports/k6/<name>
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

name="${DEPLOY_DRAIN_GATE_NAME:-transaction-read-deploy-drain-under-load-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${DEPLOY_DRAIN_GATE_INPUT_TSV:-}"
output_dir="${DEPLOY_DRAIN_GATE_OUTPUT_DIR:-build/reports/k6/${name}}"
execution_gate="tools/test/run-transaction-read-oci-evidence-execution-gate.sh"
report_md="${output_dir}/${name}-deploy-drain-under-load.md"

require_file() {
  local key="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${key} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[transaction-read-deploy-drain] name=${name}"
  echo "[transaction-read-deploy-drain] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-deploy-drain] output_dir=${output_dir}"
  echo "[transaction-read-deploy-drain] paced_load_required=true"
  echo "[transaction-read-deploy-drain] actions=backend-restart,blue-green-drain"
  echo "[transaction-read-deploy-drain] execution_gate=${execution_gate}"
  echo "[transaction-read-deploy-drain] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "DEPLOY_DRAIN_GATE_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "DEPLOY_DRAIN_GATE_INPUT_TSV" "${input_tsv}"
mkdir -p "${output_dir}"

gate_output="$(
  OCI_EVIDENCE_EXECUTION_NAME="${name}" \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
  OCI_EVIDENCE_EXECUTION_REQUIRED_SCENARIOS=deploy-drain \
  OCI_EVIDENCE_EXECUTION_DEPLOY_MIN_DURATION_MIN=5 \
    "${execution_gate}"
)"
execution_report="$(tail -1 <<<"${gate_output}")"

cat >"${report_md}" <<REPORT
# Transaction Read Deploy Drain Under Load

## Summary

- gate_status=pass
- paced load 중 backend restart/blue-green drain
- 5xx/499/unknown 429 hard-zero
- deploy event artifact: required
- deploy retry/reconnect and 499 budget artifact: required
- execution gate report: ${execution_report}

## Contract Notes

- deploy/restart/drain은 정상 트래픽 pacing 중 실행한 evidence만 인정한다.
- deploy event ref와 Nginx/Spring/Hikari/PostgreSQL timeline ref가 같은 run id로 묶여야 한다.
- retry/reconnect contract와 499 budget ref로 client-visible drain 결과를 닫는다.
- unknown 429, 499, 5xx, Hikari warning은 execution gate에서 hard-zero로 검증한다.

## Artifacts

- input TSV: ${input_tsv}
- report: ${report_md}
REPORT

echo "${report_md}"
