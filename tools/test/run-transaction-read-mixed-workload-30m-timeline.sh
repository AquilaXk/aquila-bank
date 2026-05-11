#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-mixed-workload-30m-timeline.sh [--print-plan]

Environment:
  MIXED_30M_TIMELINE_NAME        default transaction-read-mixed-30m-timeline-<timestamp>
  MIXED_30M_TIMELINE_INPUT_TSV   required OCI evidence manifest with mixed-workload-30m row
  MIXED_30M_TIMELINE_OUTPUT_DIR  default build/reports/k6/<name>
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

name="${MIXED_30M_TIMELINE_NAME:-transaction-read-mixed-30m-timeline-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${MIXED_30M_TIMELINE_INPUT_TSV:-}"
output_dir="${MIXED_30M_TIMELINE_OUTPUT_DIR:-build/reports/k6/${name}}"
execution_gate="tools/test/run-transaction-read-oci-evidence-execution-gate.sh"
report_md="${output_dir}/${name}-mixed-workload-30m-timeline.md"

require_file() {
  local key="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${key} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[transaction-read-mixed-30m-timeline] name=${name}"
  echo "[transaction-read-mixed-30m-timeline] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-mixed-30m-timeline] output_dir=${output_dir}"
  echo "[transaction-read-mixed-30m-timeline] min_duration_min=30"
  echo "[transaction-read-mixed-30m-timeline] workload=read,write,auth,notification,sse"
  echo "[transaction-read-mixed-30m-timeline] execution_gate=${execution_gate}"
  echo "[transaction-read-mixed-30m-timeline] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "MIXED_30M_TIMELINE_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "MIXED_30M_TIMELINE_INPUT_TSV" "${input_tsv}"
mkdir -p "${output_dir}"

gate_output="$(
  OCI_EVIDENCE_EXECUTION_NAME="${name}" \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
  OCI_EVIDENCE_EXECUTION_REQUIRED_SCENARIOS=mixed-workload-30m \
  OCI_EVIDENCE_EXECUTION_MIXED_MIN_DURATION_MIN=30 \
    "${execution_gate}"
)"
execution_report="$(tail -1 <<<"${gate_output}")"

cat >"${report_md}" <<REPORT
# Transaction Read Mixed Workload 30m Timeline

## Summary

- gate_status=pass
- read + write interference + auth + SSE/notification
- Prometheus/Grafana long timeline artifact: required
- p95/p99.9, 429 source, 499/5xx, Hikari pending/warning hard gate
- read/write/auth/notification/SSE components: required
- read p99.9 and 429 source artifact: required
- hot/cold/archive read bucket artifact: required
- write 2xx and status classification artifact: required
- write accepted ratio guardrail: required
- workload mix/component and outbox lag artifact: required
- execution gate report: ${execution_report}

## Contract Notes

- read-only capacity와 운영 혼합 부하는 분리해서 본다.
- mixed workload는 read/write/auth/notification/SSE component가 모두 있어야 운영 간섭 증거로 인정한다.
- mixed workload는 hot/cold/archive read bucket과 write status classification을 같이 남겨야 한다.
- unknown 429, 499, 5xx, Hikari warning, Hikari pending은 execution gate에서 hard-zero로 검증한다.
- outbox lag는 혼합 부하에서 write/notification 간섭을 닫는 hard-zero evidence로 본다.
- timeline ref는 k6, Nginx upstream, Spring metric, Hikari, PostgreSQL wait를 같은 run id로 묶는 기준이다.

## Artifacts

- input TSV: ${input_tsv}
- report: ${report_md}
REPORT

echo "${report_md}"
