#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-deploy-drain-under-load-gate.sh [--print-plan]

Environment:
  DEPLOY_DRAIN_GATE_NAME        default transaction-read-deploy-drain-under-load-<timestamp>
  DEPLOY_DRAIN_GATE_INPUT_TSV   required OCI evidence manifest with deploy-drain row
  DEPLOY_DRAIN_GATE_OUTPUT_DIR  default build/reports/k6/<name>
  DEPLOY_DRAIN_GATE_499_BUDGET_COUNT default 0
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
max_499_budget_count="${DEPLOY_DRAIN_GATE_499_BUDGET_COUNT:-0}"
execution_gate="tools/test/run-transaction-read-oci-evidence-execution-gate.sh"
summary_tsv="${output_dir}/${name}-deploy-drain-under-load.tsv"
report_md="${output_dir}/${name}-deploy-drain-under-load.md"

require_file() {
  local key="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${key} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

require_non_negative_integer() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${key} must be a non-negative integer: ${value}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[transaction-read-deploy-drain] name=${name}"
  echo "[transaction-read-deploy-drain] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-deploy-drain] output_dir=${output_dir}"
  echo "[transaction-read-deploy-drain] paced_load_required=true"
  echo "[transaction-read-deploy-drain] actions=backend-restart,blue-green-drain"
  echo "[transaction-read-deploy-drain] 499_budget_count=${max_499_budget_count}"
  echo "[transaction-read-deploy-drain] client_retry_required=true"
  echo "[transaction-read-deploy-drain] client_reconnect_required=true"
  echo "[transaction-read-deploy-drain] execution_gate=${execution_gate}"
  echo "[transaction-read-deploy-drain] summary_tsv=${summary_tsv}"
  echo "[transaction-read-deploy-drain] report_md=${report_md}"
}

require_non_negative_integer "DEPLOY_DRAIN_GATE_499_BUDGET_COUNT" "${max_499_budget_count}"

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

awk -F '\t' -v max_499_budget_count="${max_499_budget_count}" '
function value(name, fallback) {
  if (!(name in col) || col[name] == "") return fallback
  return $(col[name])
}
function has_action(items, item) {
  return index("," items ",", "," item ",") > 0
}
function add_reason(reason_value) {
  if (reason == "ok") {
    reason = reason_value
  } else {
    reason = reason "," reason_value
  }
  status = "fail"
}
BEGIN {
  print "scenario\tstatus\treason\trun_id\tdeploy_actions\tnginx_499_count\tdeploy_499_budget_count\tclient_retry_success_count\tdeploy_reconnect_success_count\tdeploy_retry_contract_ref\tdeploy_499_budget_ref"
}
NR == 1 {
  for (i = 1; i <= NF; i++) col[$i] = i
  next
}
value("scenario", "") == "deploy-drain" {
  row_count++
  status = "pass"
  reason = "ok"
  deploy_actions = value("deploy_actions", "")
  nginx_499_count = value("nginx_499_count", "999999") + 0
  deploy_499_budget_count = value("deploy_499_budget_count", "999999") + 0
  client_retry_success_count = value("client_retry_success_count", "0") + 0
  deploy_reconnect_success_count = value("deploy_reconnect_success_count", "0") + 0

  if (!has_action(deploy_actions, "backend-restart")) add_reason("backend-restart-action-missing")
  if (!has_action(deploy_actions, "blue-green-drain")) add_reason("blue-green-drain-action-missing")
  if (deploy_499_budget_count > max_499_budget_count) add_reason("499-budget-count>" max_499_budget_count)
  if (nginx_499_count > deploy_499_budget_count) add_reason("499-count>budget")
  if (client_retry_success_count <= 0) add_reason("client-retry-success-missing")
  if (deploy_reconnect_success_count <= 0) add_reason("client-reconnect-success-missing")

  if (status == "fail") fail_count++
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    value("scenario", ""),
    status,
    reason,
    value("run_id", ""),
    deploy_actions,
    nginx_499_count,
    deploy_499_budget_count,
    client_retry_success_count,
    deploy_reconnect_success_count,
    value("deploy_retry_contract_ref", ""),
    value("deploy_499_budget_ref", "")
}
END {
  if (row_count != 1) {
    print "deploy-drain\tfail\tdeploy-drain-row-count!=" row_count "\tn/a\tn/a\t0\t0\t0\t0\tn/a\tn/a"
    fail_count++
  }
  print "fail_count=" (fail_count + 0) > "/dev/stderr"
}
' "${input_tsv}" >"${summary_tsv}" 2>"${summary_tsv}.meta"

fail_count="$(awk -F '=' '/^fail_count=/ { print $2 }' "${summary_tsv}.meta")"
if [[ "${fail_count}" != "0" ]]; then
  echo "transaction read deploy drain evidence failed: ${summary_tsv}" >&2
  exit 1
fi

cat >"${report_md}" <<REPORT
# Transaction Read Deploy Drain Under Load

## Summary

- gate_status=pass
- paced load 중 backend restart/blue-green drain
- 5xx/499/unknown 429 hard-zero
- deploy event artifact: required
- deploy retry/reconnect and 499 budget artifact: required
- deploy actions: backend-restart,blue-green-drain
- 499 budget count: ${max_499_budget_count}
- client retry success: required
- client reconnect success: required
- execution gate report: ${execution_report}

## Contract Notes

- deploy/restart/drain은 정상 트래픽 pacing 중 실행한 evidence만 인정한다.
- deploy event ref와 Nginx/Spring/Hikari/PostgreSQL timeline ref가 같은 run id로 묶여야 한다.
- retry/reconnect contract와 499 budget ref로 client-visible drain 결과를 닫는다.
- deploy action과 client retry 성공 count가 없으면 배포 중 client-visible 방어 증거로 인정하지 않는다.
- unknown 429, 499, 5xx, Hikari warning은 execution gate에서 hard-zero로 검증한다.

## Artifacts

- input TSV: ${input_tsv}
- summary TSV: ${summary_tsv}
- report: ${report_md}
REPORT

echo "${report_md}"
