#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-mixed-workload-oci-k6.sh"
k6_script="ops/k6/transaction-read-mixed-workload-100m.js"

echo "[transaction-read-mixed-workload-oci-k6] files exist"
test -f "${runner}"
test -f "${k6_script}"

echo "[transaction-read-mixed-workload-oci-k6] shell syntax"
bash -n "${runner}"

echo "[transaction-read-mixed-workload-oci-k6] k6 mixed endpoints"
grep -F "/api/v1/transactions" "${k6_script}" >/dev/null
grep -F "/api/v1/transfers" "${k6_script}" >/dev/null
grep -F "/api/v1/auth/sessions" "${k6_script}" >/dev/null
grep -F "/api/v1/notifications" "${k6_script}" >/dev/null
grep -F "/api/v1/notifications/stream" "${k6_script}" >/dev/null
grep -F "aquila_mixed_read_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_read_hot_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_read_cold_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_read_archive_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_write_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_write_2xx_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_write_429_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_write_edge_429_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_write_backend_429_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_write_unknown_429_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_write_unexpected_status_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_write_accepted_ratio" "${k6_script}" >/dev/null
grep -F "K6_MIXED_WRITE_ACCEPTED_RATIO_THRESHOLD" "${k6_script}" >/dev/null
grep -F "aquila_mixed_write_401_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_write_403_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_write_409_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_write_422_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_auth_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_notification_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_sse_connect_count" "${k6_script}" >/dev/null
grep -F 'checks: ["rate==1"]' "${k6_script}" >/dev/null
grep -F 'path: "/api/v1/transactions/archive"' "${k6_script}" >/dev/null
grep -F "function mixedWriteIdempotencyKey(" "${k6_script}" >/dev/null
grep -F "function rejectedSource(" "${k6_script}" >/dev/null
grep -F "const IDEMPOTENCY_KEY_MAX_LENGTH = 80" "${k6_script}" >/dev/null
grep -F "stableHashSegment(runId)" "${k6_script}" >/dev/null
grep -F '"Idempotency-Key": mixedWriteIdempotencyKey(__VU, __ITER)' "${k6_script}" >/dev/null
if grep -F '"Idempotency-Key": `mixed-${runId}-${__VU}-${__ITER}-${Date.now()}`' "${k6_script}" >/dev/null; then
  echo "mixed workload write idempotency key must stay within the API 80 character contract" >&2
  exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

output_dir="${temp_dir}/output"
token_file="${temp_dir}/token.secret"
printf "secret-token-value\n" >"${token_file}"

fake_bin="${temp_dir}/bin"
fake_remote_reports="${temp_dir}/remote-reports"
fake_docker_log="${temp_dir}/fake-docker.log"
mkdir -p "${fake_bin}" "${fake_remote_reports}"
cat >"${fake_bin}/docker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf "%s\n" "$*" >>"${FAKE_DOCKER_LOG}"

last_arg="${!#}"
if [[ "${last_arg}" == *"-summary.json" ]]; then
  cat "${FAKE_REMOTE_REPORTS}/${last_arg}"
  exit 0
fi
if [[ "${last_arg}" == *"-summary.md" ]]; then
  cat "${FAKE_REMOTE_REPORTS}/${last_arg}"
  exit 0
fi

if [[ "$*" == *"grafana/k6:0.54.0 run /scripts/transaction-read-mixed-workload-100m.js"* ]]; then
  mkdir -p "${FAKE_REMOTE_REPORTS}"
  cat >"${FAKE_REMOTE_REPORTS}/${FAKE_K6_REPORT_NAME}-summary.json" <<JSON
{
  "metrics": {
    "aquila_mixed_read_count": {"values": {"count": 12}},
    "aquila_mixed_read_duration_ms": {"values": {"p(95)": 80, "p(99)": 120, "p(99.9)": 180, "max": 240}},
    "aquila_mixed_read_edge_429_rate": {"values": {"rate": 0.01}},
    "aquila_mixed_read_backend_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_unknown_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_hot_count": {"values": {"count": 4}},
    "aquila_mixed_read_hot_duration_ms": {"values": {"p(95)": 70, "p(99.9)": 130}},
    "aquila_mixed_read_hot_edge_429_rate": {"values": {"rate": 0.01}},
    "aquila_mixed_read_hot_backend_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_hot_unknown_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_cold_count": {"values": {"count": 4}},
    "aquila_mixed_read_cold_duration_ms": {"values": {"p(95)": 82, "p(99.9)": 150}},
    "aquila_mixed_read_cold_edge_429_rate": {"values": {"rate": 0.02}},
    "aquila_mixed_read_cold_backend_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_cold_unknown_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_archive_count": {"values": {"count": 4}},
    "aquila_mixed_read_archive_duration_ms": {"values": {"p(95)": 88, "p(99.9)": 180}},
    "aquila_mixed_read_archive_edge_429_rate": {"values": {"rate": 0.03}},
    "aquila_mixed_read_archive_backend_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_archive_unknown_429_count": {"values": {"count": 0}},
    "aquila_mixed_write_count": {"values": {"count": 9}},
    "aquila_mixed_write_duration_ms": {"values": {"p(95)": 95, "p(99)": 140, "p(99.9)": 170, "max": 210}},
    "aquila_mixed_write_2xx_count": {"values": {"count": 4}},
    "aquila_mixed_write_429_count": {"values": {"count": 2}},
    "aquila_mixed_write_edge_429_count": {"values": {"count": 1}},
    "aquila_mixed_write_backend_429_count": {"values": {"count": 1}},
    "aquila_mixed_write_unknown_429_count": {"values": {"count": 0}},
    "aquila_mixed_write_unexpected_status_count": {"values": {"count": 3}},
    "aquila_mixed_write_401_count": {"values": {"count": 1}},
    "aquila_mixed_write_403_count": {"values": {"count": 1}},
    "aquila_mixed_write_409_count": {"values": {"count": 1}},
    "aquila_mixed_write_422_count": {"values": {"count": 0}},
    "aquila_mixed_write_other_unexpected_count": {"values": {"count": 0}},
    "aquila_mixed_write_429_rate": {"values": {"rate": 0.2222222222}},
    "aquila_mixed_write_accepted_ratio": {"values": {"rate": 0.4444444444}},
    "aquila_mixed_auth_count": {"values": {"count": 3}},
    "aquila_mixed_auth_duration_ms": {"values": {"p(95)": 42}},
    "aquila_mixed_notification_count": {"values": {"count": 3}},
    "aquila_mixed_notification_duration_ms": {"values": {"p(95)": 38}},
    "aquila_mixed_sse_connect_count": {"values": {"count": 1}},
    "aquila_mixed_5xx_count": {"values": {"count": 0}},
    "checks": {"values": {"rate": 0.9, "passes": 18, "fails": 2}}
  }
}
JSON
  cat >"${FAKE_REMOTE_REPORTS}/${FAKE_K6_REPORT_NAME}-summary.md" <<MD
# Fake k6 summary
MD
  echo "thresholds on metrics 'checks' have been crossed" >&2
  exit 99
fi

exit 0
SH
chmod +x "${fake_bin}/docker"

echo "[transaction-read-mixed-workload-oci-k6] live plan"
plan="$(
  MIXED_WORKLOAD_OCI_NAME=mixed-oci-check \
  MIXED_WORKLOAD_OCI_RUN_ID=mixed-oci-run-001 \
  MIXED_WORKLOAD_OCI_OUTPUT_DIR="${output_dir}" \
  MIXED_WORKLOAD_OCI_DOCKER_CONTEXT=oci-k6-generator \
  MIXED_WORKLOAD_OCI_BASE_URL=http://192.0.2.20:8080 \
  MIXED_WORKLOAD_OCI_REMOTE_WORKDIR=/srv/aquila-bank \
  MIXED_WORKLOAD_OCI_DURATION=30m \
  MIXED_WORKLOAD_OCI_HOT_ACCOUNT_ID=101 \
  MIXED_WORKLOAD_OCI_HOT_FROM=2026-04-01T00:00:00Z \
  MIXED_WORKLOAD_OCI_HOT_TO=2026-04-30T23:59:59Z \
  MIXED_WORKLOAD_OCI_COLD_ACCOUNT_ID=202 \
  MIXED_WORKLOAD_OCI_COLD_FROM=2026-01-01T00:00:00Z \
  MIXED_WORKLOAD_OCI_COLD_TO=2026-01-31T23:59:59Z \
  MIXED_WORKLOAD_OCI_ARCHIVE_ACCOUNT_ID=303 \
  MIXED_WORKLOAD_OCI_ARCHIVE_FROM=2026-01-01T00:00:00Z \
  MIXED_WORKLOAD_OCI_ARCHIVE_TO=2026-01-31T23:59:59Z \
  MIXED_WORKLOAD_OCI_WRITE_SOURCE_ACCOUNT_ID=920000001 \
  MIXED_WORKLOAD_OCI_WRITE_TARGET_ACCOUNT_ID=920000002 \
  MIXED_WORKLOAD_OCI_AUTH_TOKEN_FILE="${token_file}" \
    bash "${runner}" --print-plan
)"
grep -F "mode=live" <<<"${plan}" >/dev/null
grep -F "k6_script=ops/k6/transaction-read-mixed-workload-100m.js" <<<"${plan}" >/dev/null
grep -F "docker_context=oci-k6-generator" <<<"${plan}" >/dev/null
grep -F "base_url=configured" <<<"${plan}" >/dev/null
grep -F "remote_workdir=/srv/aquila-bank" <<<"${plan}" >/dev/null
grep -F "duration=30m" <<<"${plan}" >/dev/null
grep -F "components=read,write,auth,notification,sse" <<<"${plan}" >/dev/null
grep -F "write_accepted_ratio_threshold=0.80" <<<"${plan}" >/dev/null
grep -F "auth_token=present source=file" <<<"${plan}" >/dev/null
grep -F "evidence_env=${output_dir}/mixed-oci-check-oci-mixed-evidence.env" <<<"${plan}" >/dev/null
if grep -F "secret-token-value" <<<"${plan}" >/dev/null; then
  echo "auth token leaked into mixed workload OCI plan output" >&2
  exit 1
fi
if grep -F "http://192.0.2.20:8080" <<<"${plan}" >/dev/null; then
  echo "raw OCI base URL leaked into mixed workload OCI plan output" >&2
  exit 1
fi

echo "[transaction-read-mixed-workload-oci-k6] missing live runtime env fails sanitized"
if MIXED_WORKLOAD_OCI_NAME=mixed-oci-missing bash "${runner}" --print-plan >"${temp_dir}/missing.log" 2>&1; then
  echo "missing runtime env unexpectedly passed" >&2
  exit 1
fi
grep -F "MIXED_WORKLOAD_OCI_DOCKER_CONTEXT is required" "${temp_dir}/missing.log" >/dev/null

echo "[transaction-read-mixed-workload-oci-k6] fixture artifact generation"
generated_env="$(
  MIXED_WORKLOAD_OCI_MODE=fixture \
  MIXED_WORKLOAD_OCI_NAME=mixed-oci-fixture \
  MIXED_WORKLOAD_OCI_RUN_ID=mixed-oci-run-fixture \
  MIXED_WORKLOAD_OCI_OUTPUT_DIR="${output_dir}/fixture" \
    bash "${runner}" | tail -1
)"
test "${generated_env}" = "${output_dir}/fixture/mixed-oci-fixture-oci-mixed-evidence.env"
test -f "${generated_env}"

# shellcheck disable=SC1090
source "${generated_env}"
test "${MIXED_WORKLOAD_RUNNER_ENV_FORMAT}" = "oci-mixed-v1"
test "${MIXED_WORKLOAD_RUNNER_RUN_SCRIPT}" = "${runner}"
test "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENTS}" = "read,write,auth,notification,sse"
test -f "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}"
test -f "${MIXED_WORKLOAD_RUNNER_WORKLOAD_MIX_REF}"
test -f "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}"
test -f "${MIXED_WORKLOAD_RUNNER_OUTBOX_LAG_REF}"
test -f "${MIXED_WORKLOAD_RUNNER_READ_429_SOURCE_REF}"
test -f "${MIXED_WORKLOAD_RUNNER_READ_BUCKET_REF}"
test -f "${MIXED_WORKLOAD_RUNNER_WRITE_STATUS_REF}"
test -f "${MIXED_WORKLOAD_RUNNER_WRITE_429_SOURCE_REF}"
grep -F $'read\tpass\t' "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}" >/dev/null
grep -F $'write\tpass\t' "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}" >/dev/null
grep -F $'auth\tpass\t' "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}" >/dev/null
grep -F $'notification\tpass\t' "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}" >/dev/null
grep -F $'sse\tpass\t' "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}" >/dev/null
grep -F $'mixed-oci-run-fixture\t0' "${MIXED_WORKLOAD_RUNNER_OUTBOX_LAG_REF}" >/dev/null
grep -F $'mixed-oci-run-fixture\thot\tnginx-edge\t0.03\t0\t0' "${MIXED_WORKLOAD_RUNNER_READ_429_SOURCE_REF}" >/dev/null
grep -F $'mixed-oci-run-fixture\tcold\tnginx-edge\t0.05\t0\t0' "${MIXED_WORKLOAD_RUNNER_READ_429_SOURCE_REF}" >/dev/null
grep -F $'mixed-oci-run-fixture\tarchive\tnginx-edge\t0.08\t0\t0' "${MIXED_WORKLOAD_RUNNER_READ_429_SOURCE_REF}" >/dev/null
grep -F $'hot\t44\t72\t350\t0.03\t0\t0' "${MIXED_WORKLOAD_RUNNER_READ_BUCKET_REF}" >/dev/null
grep -F $'cold\t42\t88\t410\t0.05\t0\t0' "${MIXED_WORKLOAD_RUNNER_READ_BUCKET_REF}" >/dev/null
grep -F $'archive\t42\t96\t440\t0.08\t0\t0' "${MIXED_WORKLOAD_RUNNER_READ_BUCKET_REF}" >/dev/null
grep -F $'2xx\t28' "${MIXED_WORKLOAD_RUNNER_WRITE_STATUS_REF}" >/dev/null
grep -F $'401\t1' "${MIXED_WORKLOAD_RUNNER_WRITE_STATUS_REF}" >/dev/null
grep -F $'403\t1' "${MIXED_WORKLOAD_RUNNER_WRITE_STATUS_REF}" >/dev/null
grep -F $'409\t1' "${MIXED_WORKLOAD_RUNNER_WRITE_STATUS_REF}" >/dev/null
grep -F $'mixed-oci-run-fixture\tedge\t1' "${MIXED_WORKLOAD_RUNNER_WRITE_429_SOURCE_REF}" >/dev/null
grep -F $'mixed-oci-run-fixture\tbackend\t0' "${MIXED_WORKLOAD_RUNNER_WRITE_429_SOURCE_REF}" >/dev/null
grep -F $'mixed-oci-run-fixture\tunknown\t0' "${MIXED_WORKLOAD_RUNNER_WRITE_429_SOURCE_REF}" >/dev/null

echo "[transaction-read-mixed-workload-oci-k6] fixture summary metrics"
jq -e '.metrics.aquila_mixed_read_count.values.count == 128' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_read_hot_count.values.count == 44' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_read_cold_count.values.count == 42' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_read_archive_count.values.count == 42' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_count.values.count == 32' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_2xx_count.values.count == 28' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_429_count.values.count == 1' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_edge_429_count.values.count == 1' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_backend_429_count.values.count == 0' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_unknown_429_count.values.count == 0' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_unexpected_status_count.values.count == 3' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_accepted_ratio.values.rate == 0.875' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_auth_count.values.count == 16' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_notification_count.values.count == 16' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_sse_connect_count.values.count == 1' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null

test "${MIXED_WORKLOAD_RUNNER_WRITE_2XX_COUNT}" = "28"
test "${MIXED_WORKLOAD_RUNNER_WRITE_429_COUNT}" = "1"
test "${MIXED_WORKLOAD_RUNNER_WRITE_EDGE_429_COUNT}" = "1"
test "${MIXED_WORKLOAD_RUNNER_WRITE_BACKEND_429_COUNT}" = "0"
test "${MIXED_WORKLOAD_RUNNER_WRITE_UNKNOWN_429_COUNT}" = "0"
test "${MIXED_WORKLOAD_RUNNER_WRITE_UNEXPECTED_STATUS_COUNT}" = "3"
test "${MIXED_WORKLOAD_RUNNER_WRITE_ACCEPTED_RATIO}" = "0.875"
test "${MIXED_WORKLOAD_RUNNER_MIN_WRITE_ACCEPTED_RATIO}" = "0.80"
test "${MIXED_WORKLOAD_RUNNER_READ_BUCKETS}" = "hot,cold,archive"
test "${MIXED_WORKLOAD_RUNNER_READ_HOT_P999_MS}" = "350"
test "${MIXED_WORKLOAD_RUNNER_READ_COLD_P999_MS}" = "410"
test "${MIXED_WORKLOAD_RUNNER_READ_ARCHIVE_P999_MS}" = "440"
grep -F $'write\tpass\t32\t95\t28\t1\t3' "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}" >/dev/null

echo "[transaction-read-mixed-workload-oci-k6] failed k6 still collects summary"
live_output_dir="${output_dir}/live-threshold-failure"
set +e
PATH="${fake_bin}:${PATH}" \
FAKE_DOCKER_LOG="${fake_docker_log}" \
FAKE_REMOTE_REPORTS="${fake_remote_reports}" \
FAKE_K6_REPORT_NAME="mixed-oci-live-failed-k6" \
MIXED_WORKLOAD_OCI_NAME=mixed-oci-live-failed \
MIXED_WORKLOAD_OCI_RUN_ID=mixed-oci-run-live-failed \
MIXED_WORKLOAD_OCI_OUTPUT_DIR="${live_output_dir}" \
MIXED_WORKLOAD_OCI_K6_REPORT_NAME=mixed-oci-live-failed-k6 \
MIXED_WORKLOAD_OCI_DOCKER_CONTEXT=oci-k6-generator \
MIXED_WORKLOAD_OCI_BASE_URL=http://192.0.2.20:8080 \
MIXED_WORKLOAD_OCI_REMOTE_WORKDIR=/srv/aquila-bank \
MIXED_WORKLOAD_OCI_DURATION=30m \
MIXED_WORKLOAD_OCI_HOT_ACCOUNT_ID=101 \
MIXED_WORKLOAD_OCI_HOT_FROM=2026-04-01T00:00:00Z \
MIXED_WORKLOAD_OCI_HOT_TO=2026-04-30T23:59:59Z \
MIXED_WORKLOAD_OCI_COLD_ACCOUNT_ID=202 \
MIXED_WORKLOAD_OCI_COLD_FROM=2026-01-01T00:00:00Z \
MIXED_WORKLOAD_OCI_COLD_TO=2026-01-31T23:59:59Z \
MIXED_WORKLOAD_OCI_ARCHIVE_ACCOUNT_ID=303 \
MIXED_WORKLOAD_OCI_ARCHIVE_FROM=2026-01-01T00:00:00Z \
MIXED_WORKLOAD_OCI_ARCHIVE_TO=2026-01-31T23:59:59Z \
MIXED_WORKLOAD_OCI_WRITE_SOURCE_ACCOUNT_ID=920000001 \
MIXED_WORKLOAD_OCI_WRITE_TARGET_ACCOUNT_ID=920000002 \
MIXED_WORKLOAD_OCI_AUTH_TOKEN_FILE="${token_file}" \
  bash "${runner}" >"${temp_dir}/live-failed.log" 2>"${temp_dir}/live-failed.err"
live_failed_status="$?"
set -e
test "${live_failed_status}" = "99"
live_generated_env="$(awk 'NF { line = $0 } END { print line }' "${temp_dir}/live-failed.log")"
test -f "${live_generated_env}"

# shellcheck disable=SC1090
source "${live_generated_env}"
test -f "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}"
test -f "${live_output_dir}/mixed-oci-live-failed-oci-mixed-failure.md"
jq -e '.metrics.aquila_mixed_write_2xx_count.values.count == 4' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_429_count.values.count == 2' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_edge_429_count.values.count == 1' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_backend_429_count.values.count == 1' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_unknown_429_count.values.count == 0' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_unexpected_status_count.values.count == 3' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_accepted_ratio.values.rate == 0.4444444444' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
test "${MIXED_WORKLOAD_RUNNER_WRITE_2XX_COUNT}" = "4"
test "${MIXED_WORKLOAD_RUNNER_WRITE_429_COUNT}" = "2"
test "${MIXED_WORKLOAD_RUNNER_WRITE_EDGE_429_COUNT}" = "1"
test "${MIXED_WORKLOAD_RUNNER_WRITE_BACKEND_429_COUNT}" = "1"
test "${MIXED_WORKLOAD_RUNNER_WRITE_UNKNOWN_429_COUNT}" = "0"
test "${MIXED_WORKLOAD_RUNNER_WRITE_UNEXPECTED_STATUS_COUNT}" = "3"
test "${MIXED_WORKLOAD_RUNNER_WRITE_ACCEPTED_RATIO}" = "0.4444444444"
test "${MIXED_WORKLOAD_RUNNER_MIN_WRITE_ACCEPTED_RATIO}" = "0.80"
test -f "${MIXED_WORKLOAD_RUNNER_WRITE_429_SOURCE_REF}"
grep -F $'mixed-oci-run-live-failed\tedge\t1' "${MIXED_WORKLOAD_RUNNER_WRITE_429_SOURCE_REF}" >/dev/null
grep -F $'mixed-oci-run-live-failed\tbackend\t1' "${MIXED_WORKLOAD_RUNNER_WRITE_429_SOURCE_REF}" >/dev/null
grep -F $'mixed-oci-run-live-failed\tunknown\t0' "${MIXED_WORKLOAD_RUNNER_WRITE_429_SOURCE_REF}" >/dev/null
grep -F $'write\tpass\t9\t95\t4\t2\t3' "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}" >/dev/null
grep -F "OCI mixed workload k6 run failed" "${temp_dir}/live-failed.err" >/dev/null
if grep -F "secret-token-value" "${temp_dir}/live-failed.log" "${temp_dir}/live-failed.err" >/dev/null; then
  echo "auth token leaked into failed live mixed workload output" >&2
  exit 1
fi
if grep -F "http://192.0.2.20:8080" "${temp_dir}/live-failed.log" "${temp_dir}/live-failed.err" >/dev/null; then
  echo "raw OCI base URL leaked into failed live mixed workload output" >&2
  exit 1
fi
