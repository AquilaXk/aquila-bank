#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-10m-soak-slo-gate.sh"

echo "[transaction-read-10m-soak] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_json="${temp_dir}/soak-summary.json"
nginx_status="${temp_dir}/nginx-status.tsv"
hikari_log="${temp_dir}/hikari.log"
resource_snapshot="${temp_dir}/resource.tsv"
output_dir="${temp_dir}/output"

cat >"${summary_json}" <<'JSON'
{
  "metrics": {
    "aquila_transaction_429_rate": {"values": {"rate": 0.060}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": 0.040}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": 0.020}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_edge_delayed_rate": {"values": {"rate": 0.12}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": 310}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(95)": 315}},
    "aquila_transaction_hot_deep_cursor_ms": {"values": {"p(95)": 320}},
    "aquila_transaction_cold_first_ms": {"values": {"p(95)": 305}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(95)": 310}},
    "aquila_transaction_cold_deep_cursor_ms": {"values": {"p(95)": 325}}
  }
}
JSON

cat >"${nginx_status}" <<'TSV'
run	status	limit_req_status	count
soak-10m	200	PASSED	9000
soak-10m	200	DELAYED	1200
soak-10m	429	REJECTED	600
TSV

: >"${hikari_log}"

cat >"${resource_snapshot}" <<'TSV'
component	cpu_percent	memory_mib	note
backend	68.2	720	oci-a1
postgres	46.1	2300	data-200gb
nginx	4.2	72	edge
TSV

echo "[transaction-read-10m-soak] print plan"
plan="$(
  SOAK_10M_GATE_NAME=soak-check \
  SOAK_10M_SUMMARY_JSON="${summary_json}" \
  SOAK_10M_NGINX_STATUS_TSV="${nginx_status}" \
  SOAK_10M_HIKARI_LOG="${hikari_log}" \
  SOAK_10M_RESOURCE_SNAPSHOT_TSV="${resource_snapshot}" \
  SOAK_10M_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=soak-check" <<<"${plan}" >/dev/null
grep -F "duration=10m" <<<"${plan}" >/dev/null
grep -F "summary_json=${summary_json}" <<<"${plan}" >/dev/null
grep -F "hikari_log=${hikari_log}" <<<"${plan}" >/dev/null

echo "[transaction-read-10m-soak] report"
output="$(
  SOAK_10M_GATE_NAME=soak-check \
  SOAK_10M_SUMMARY_JSON="${summary_json}" \
  SOAK_10M_NGINX_STATUS_TSV="${nginx_status}" \
  SOAK_10M_HIKARI_LOG="${hikari_log}" \
  SOAK_10M_RESOURCE_SNAPSHOT_TSV="${resource_snapshot}" \
  SOAK_10M_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
test "${report_md}" = "${output_dir}/soak-check-10m-soak.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "| accepted p95 ms | 325 |" "${report_md}" >/dev/null
grep -F "| total 429 rate | 0.060 |" "${report_md}" >/dev/null
grep -F "| 499 count | 0 |" "${report_md}" >/dev/null
grep -F "| 5xx count | 0 |" "${report_md}" >/dev/null
grep -F "| Hikari validation warnings | 0 |" "${report_md}" >/dev/null
grep -F "| backend | 68.2 | 720 | oci-a1 |" "${report_md}" >/dev/null

echo "[transaction-read-10m-soak] fail report"
printf '%s\n' "Failed to validate connection org.postgresql.jdbc.PgConnection@1 (This connection has been closed.)" >"${hikari_log}"
if SOAK_10M_GATE_NAME=soak-fail \
  SOAK_10M_SUMMARY_JSON="${summary_json}" \
  SOAK_10M_NGINX_STATUS_TSV="${nginx_status}" \
  SOAK_10M_HIKARI_LOG="${hikari_log}" \
  SOAK_10M_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "10m soak gate unexpectedly passed Hikari warning" >&2
  exit 1
fi

echo "[transaction-read-10m-soak] Hikari profile contract"
grep -F 'max-lifetime: ${OCI_A1_DB_MAX_LIFETIME_MS:900000}' back/src/main/resources/application-oci-a1.yml >/dev/null
grep -F 'keepalive-time: ${OCI_A1_DB_KEEPALIVE_TIME_MS:120000}' back/src/main/resources/application-oci-a1.yml >/dev/null
grep -F 'validation-timeout: ${OCI_A1_DB_VALIDATION_TIMEOUT_MS:1000}' back/src/main/resources/application-oci-a1.yml >/dev/null

echo "[transaction-read-10m-soak] invalid input fails"
if SOAK_10M_SUMMARY_JSON="${summary_json}" SOAK_10M_DURATION=30s "${runner}" --print-plan >/dev/null 2>&1; then
  echo "short soak duration unexpectedly succeeded" >&2
  exit 1
fi
