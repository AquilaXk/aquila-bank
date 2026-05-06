#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-oci-k6-service-auth-arrival16-evidence-gate.sh [--print-plan]

Environment:
  SERVICE_AUTH_EVIDENCE_NAME                 default oci-k6-service-auth-arrival16-<timestamp>
  SERVICE_AUTH_EVIDENCE_K6_SUMMARY_JSON      required k6 summary JSON
  SERVICE_AUTH_EVIDENCE_NGINX_AGGREGATE_TSV  required Nginx aggregate TSV
  SERVICE_AUTH_EVIDENCE_429_SOURCE_TSV       required 429 source gate TSV
  SERVICE_AUTH_EVIDENCE_PACING_SUMMARY       required pacing summary markdown
  SERVICE_AUTH_EVIDENCE_AUTH_PREFLIGHT_LOG   required auth preflight log
  SERVICE_AUTH_EVIDENCE_OUTPUT_DIR           default build/reports/k6/<name>/service-auth-evidence
  SERVICE_AUTH_EVIDENCE_EXPECTED_RATE        default 16
  SERVICE_AUTH_EVIDENCE_EXPECTED_TIME_UNIT   default 1s
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

name="${SERVICE_AUTH_EVIDENCE_NAME:-oci-k6-service-auth-arrival16-$(date +%Y-%m-%d-%H%M%S)}"
summary_json="${SERVICE_AUTH_EVIDENCE_K6_SUMMARY_JSON:-}"
nginx_aggregate_tsv="${SERVICE_AUTH_EVIDENCE_NGINX_AGGREGATE_TSV:-}"
source_429_tsv="${SERVICE_AUTH_EVIDENCE_429_SOURCE_TSV:-}"
pacing_summary="${SERVICE_AUTH_EVIDENCE_PACING_SUMMARY:-}"
auth_preflight_log="${SERVICE_AUTH_EVIDENCE_AUTH_PREFLIGHT_LOG:-}"
output_dir="${SERVICE_AUTH_EVIDENCE_OUTPUT_DIR:-build/reports/k6/${name}/service-auth-evidence}"
expected_rate="${SERVICE_AUTH_EVIDENCE_EXPECTED_RATE:-16}"
expected_time_unit="${SERVICE_AUTH_EVIDENCE_EXPECTED_TIME_UNIT:-1s}"
summary_tsv="${output_dir}/${name}-service-auth-evidence.tsv"
report_md="${output_dir}/${name}-service-auth-evidence.md"

print_plan() {
  echo "[oci-k6-service-auth-evidence] name=${name}"
  echo "[oci-k6-service-auth-evidence] expected_arrival_rate=${expected_rate}/${expected_time_unit}"
  echo "[oci-k6-service-auth-evidence] k6_summary_json=${summary_json:-missing}"
  echo "[oci-k6-service-auth-evidence] nginx_aggregate_tsv=${nginx_aggregate_tsv:-missing}"
  echo "[oci-k6-service-auth-evidence] source_429_tsv=${source_429_tsv:-missing}"
  echo "[oci-k6-service-auth-evidence] pacing_summary=${pacing_summary:-missing}"
  echo "[oci-k6-service-auth-evidence] auth_preflight_log=${auth_preflight_log:-missing}"
  echo "[oci-k6-service-auth-evidence] output_dir=${output_dir}"
  echo "[oci-k6-service-auth-evidence] summary_tsv=${summary_tsv}"
  echo "[oci-k6-service-auth-evidence] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

mkdir -p "${output_dir}"

fail_count=0
{
  printf "check\tstatus\tvalue\tthreshold\treason\n"
} >"${summary_tsv}"

append_check() {
  local check="$1"
  local status="$2"
  local value="$3"
  local threshold="$4"
  local reason="$5"
  printf "%s\t%s\t%s\t%s\t%s\n" "${check}" "${status}" "${value}" "${threshold}" "${reason}" >>"${summary_tsv}"
  if [[ "${status}" == "fail" ]]; then
    fail_count=$((fail_count + 1))
  fi
}

require_file() {
  local check="$1"
  local path="$2"
  if [[ -n "${path}" && -s "${path}" ]]; then
    append_check "${check}" "pass" "${path}" "present" "ok"
    return 0
  fi
  append_check "${check}" "fail" "${path:-missing}" "present" "missing-required-artifact"
  return 1
}

if ! command -v jq >/dev/null 2>&1; then
  append_check "jq" "fail" "missing" "present" "jq-required"
else
  append_check "jq" "pass" "present" "present" "ok"
fi

require_file "k6_summary_json" "${summary_json}" || true
require_file "nginx_aggregate_tsv" "${nginx_aggregate_tsv}" || true
require_file "source_429_tsv" "${source_429_tsv}" || true
require_file "pacing_summary" "${pacing_summary}" || true
require_file "auth_preflight_log" "${auth_preflight_log}" || true

if ((fail_count > 0)); then
  status="fail"
else
  metric_count() {
    local metric="$1"
    jq -r --arg metric "${metric}" '
      (.metrics[$metric].values // {}) as $values
      | if $values.count != null then $values.count
        elif ($values.passes != null or $values.fails != null) then (($values.passes // 0) + ($values.fails // 0))
        else 0
        end
    ' "${summary_json}"
  }

  metric_p95() {
    local metric="$1"
    jq -r --arg metric "${metric}" '(.metrics[$metric].values["p(95)"] // "")' "${summary_json}"
  }

  append_gt_zero() {
    local check="$1"
    local value="$2"
    if awk -v value="${value}" 'BEGIN { exit !(value > 0) }'; then
      append_check "${check}" "pass" "${value}" ">0" "ok"
    else
      append_check "${check}" "fail" "${value}" ">0" "not-positive"
    fi
  }

  append_zero() {
    local check="$1"
    local value="$2"
    if awk -v value="${value}" 'BEGIN { exit !(value == 0) }'; then
      append_check "${check}" "pass" "${value}" "0" "ok"
    else
      append_check "${check}" "fail" "${value}" "0" "non-zero"
    fi
  }

  append_present_number() {
    local check="$1"
    local value="$2"
    if [[ -n "${value}" ]] && awk -v value="${value}" 'BEGIN { exit !(value >= 0) }'; then
      append_check "${check}" "pass" "${value}" "present" "ok"
    else
      append_check "${check}" "fail" "${value:-missing}" "present" "missing-metric"
    fi
  }

  accepted_200_count="$(metric_count "aquila_transaction_accepted_200_count")"
  unknown_429_count="$(metric_count "aquila_transaction_unknown_429_count")"
  transaction_502_count="$(metric_count "aquila_transaction_502_count")"
  transaction_503_count="$(metric_count "aquila_transaction_503_count")"
  append_gt_zero "accepted_200_count" "${accepted_200_count}"
  append_zero "unknown_429_count" "${unknown_429_count}"
  append_zero "transaction_502_count" "${transaction_502_count}"
  append_zero "transaction_503_count" "${transaction_503_count}"

  for item in \
    "hot_first:aquila_transaction_hot_first_ms" \
    "hot_cursor:aquila_transaction_hot_cursor_ms" \
    "hot_deep_cursor:aquila_transaction_hot_deep_cursor_ms" \
    "cold_first:aquila_transaction_cold_first_ms" \
    "cold_cursor:aquila_transaction_cold_cursor_ms" \
    "cold_deep_cursor:aquila_transaction_cold_deep_cursor_ms"; do
    shape="${item%%:*}"
    metric="${item#*:}"
    append_present_number "${shape}_p95_ms" "$(metric_p95 "${metric}")"
  done

  source_gate_failures="$(awk -F '\t' 'NR > 1 && $2 == "fail" { count++ } END { print count + 0 }' "${source_429_tsv}")"
  append_zero "source_gate_failures" "${source_gate_failures}"

  nginx_transaction_read_rows="$(awk -F '\t' 'NR > 1 { count += $9 } END { print count + 0 }' "${nginx_aggregate_tsv}")"
  nginx_499_count="$(awk -F '\t' 'NR > 1 && $2 == "499" { count += $9 } END { print count + 0 }' "${nginx_aggregate_tsv}")"
  nginx_5xx_count="$(awk -F '\t' 'NR > 1 && $2 ~ /^5/ { count += $9 } END { print count + 0 }' "${nginx_aggregate_tsv}")"
  append_gt_zero "nginx_transaction_read_rows" "${nginx_transaction_read_rows}"
  append_zero "nginx_499_count" "${nginx_499_count}"
  append_zero "nginx_5xx_count" "${nginx_5xx_count}"

  if grep -F "arrival-rate gate: ${expected_rate}/${expected_time_unit}" "${pacing_summary}" >/dev/null; then
    append_check "arrival_rate_contract" "pass" "${expected_rate}/${expected_time_unit}" "${expected_rate}/${expected_time_unit}" "ok"
  else
    append_check "arrival_rate_contract" "fail" "missing" "${expected_rate}/${expected_time_unit}" "pacing-summary-mismatch"
  fi
  if grep -F "auth item preflight hot" "${auth_preflight_log}" >/dev/null; then
    append_check "hot_auth_preflight" "pass" "present" "present" "ok"
  else
    append_check "hot_auth_preflight" "fail" "missing" "present" "missing-hot-auth-preflight"
  fi
  if grep -F "auth item preflight cold" "${auth_preflight_log}" >/dev/null; then
    append_check "cold_auth_preflight" "pass" "present" "present" "ok"
  else
    append_check "cold_auth_preflight" "fail" "missing" "present" "missing-cold-auth-preflight"
  fi

  if ((fail_count > 0)); then
    status="fail"
  else
    status="pass"
  fi
fi

{
  echo "# OCI k6 Service Auth Arrival16 Evidence"
  echo
  echo "## Summary"
  echo
  echo "- gate_status=${status}"
  echo "- expected arrival rate: ${expected_rate}/${expected_time_unit}"
  echo "- k6 summary: ${summary_json:-missing}"
  echo "- Nginx aggregate: ${nginx_aggregate_tsv:-missing}"
  echo "- 429 source: ${source_429_tsv:-missing}"
  echo
  echo "## Checks"
  echo
  echo "| check | status | value | threshold | reason |"
  echo "| --- | --- | --- | --- | --- |"
  awk -F '\t' 'NR > 1 { printf "| %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5 }' "${summary_tsv}"
} >"${report_md}"

echo "${report_md}"
if [[ "${status}" != "pass" ]]; then
  echo "service-auth arrival16 evidence gate failed: ${summary_tsv}" >&2
  exit 1
fi
