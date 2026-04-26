#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-outbox-provider-small-batch-backlog-gate.sh [--print-plan|--dry-run]

Environment:
  OUTBOX_BACKLOG_NAME                 default outbox-provider-backlog-<timestamp>
  OUTBOX_BACKLOG_BASE_URL             default http://localhost:8080
  OUTBOX_BACKLOG_TOKEN                bearer token for internal outbox ops
  OUTBOX_BACKLOG_MAX_LAG_SECONDS      default 30
  OUTBOX_BACKLOG_MAX_FAILED_COUNT     default 0
  OUTBOX_BACKLOG_MAX_QUARANTINED_COUNT default 0
  OUTBOX_BACKLOG_MAX_STALE_SENDING_COUNT default 0
  OUTBOX_BACKLOG_MAX_NOTIFICATION_LAG_COUNT default 0
  OUTBOX_BACKLOG_MAX_DLQ_COUNT        default 0
  OUTBOX_BACKLOG_CHANNEL_LIMIT        default 20

Examples:
  tools/test/run-outbox-provider-small-batch-backlog-gate.sh --print-plan
  OUTBOX_BACKLOG_TOKEN=... tools/test/run-outbox-provider-small-batch-backlog-gate.sh
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    --dry-run)
      mode="dry-run"
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

require_non_negative_integer_value() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${key} must be zero or a positive integer: ${value}" >&2
    exit 1
  fi
}

require_positive_integer_value() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${key} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

name="${OUTBOX_BACKLOG_NAME:-outbox-provider-backlog-$(date +%Y-%m-%d-%H%M%S)}"
base_url="${OUTBOX_BACKLOG_BASE_URL:-http://localhost:8080}"
base_url="${base_url%/}"
token="${OUTBOX_BACKLOG_TOKEN:-}"
max_lag_seconds="${OUTBOX_BACKLOG_MAX_LAG_SECONDS:-30}"
max_failed_count="${OUTBOX_BACKLOG_MAX_FAILED_COUNT:-0}"
max_quarantined_count="${OUTBOX_BACKLOG_MAX_QUARANTINED_COUNT:-0}"
max_stale_sending_count="${OUTBOX_BACKLOG_MAX_STALE_SENDING_COUNT:-0}"
max_notification_lag_count="${OUTBOX_BACKLOG_MAX_NOTIFICATION_LAG_COUNT:-0}"
max_dlq_count="${OUTBOX_BACKLOG_MAX_DLQ_COUNT:-0}"
channel_limit="${OUTBOX_BACKLOG_CHANNEL_LIMIT:-20}"
report_dir="build/reports/outbox/${name}"
summary_tsv="${report_dir}/outbox-provider-backlog-summary.tsv"
outbox_json="${report_dir}/outbox-summary.json"
notification_json="${report_dir}/notification-summary.json"
channel_json="${report_dir}/channel-quarantined-events.json"

require_non_negative_integer_value "OUTBOX_BACKLOG_MAX_LAG_SECONDS" "${max_lag_seconds}"
require_non_negative_integer_value "OUTBOX_BACKLOG_MAX_FAILED_COUNT" "${max_failed_count}"
require_non_negative_integer_value "OUTBOX_BACKLOG_MAX_QUARANTINED_COUNT" "${max_quarantined_count}"
require_non_negative_integer_value "OUTBOX_BACKLOG_MAX_STALE_SENDING_COUNT" "${max_stale_sending_count}"
require_non_negative_integer_value "OUTBOX_BACKLOG_MAX_NOTIFICATION_LAG_COUNT" "${max_notification_lag_count}"
require_non_negative_integer_value "OUTBOX_BACKLOG_MAX_DLQ_COUNT" "${max_dlq_count}"
require_positive_integer_value "OUTBOX_BACKLOG_CHANNEL_LIMIT" "${channel_limit}"

print_plan() {
  echo "[outbox-provider-backlog] name=${name}"
  echo "[outbox-provider-backlog] base_url=${base_url}"
  echo "[outbox-provider-backlog] max_lag_seconds=${max_lag_seconds}"
  echo "[outbox-provider-backlog] max_failed_count=${max_failed_count}"
  echo "[outbox-provider-backlog] max_quarantined_count=${max_quarantined_count}"
  echo "[outbox-provider-backlog] max_stale_sending_count=${max_stale_sending_count}"
  echo "[outbox-provider-backlog] max_notification_lag_count=${max_notification_lag_count}"
  echo "[outbox-provider-backlog] max_dlq_count=${max_dlq_count}"
  echo "[outbox-provider-backlog] channel_limit=${channel_limit}"
  echo "[outbox-provider-backlog] summary=${summary_tsv}"
}

print_dry_run() {
  echo "curl ${base_url}/internal/api/v1/outbox/summary -H 'Authorization: Bearer ***'"
  echo "curl ${base_url}/internal/api/v1/outbox/notification/summary -H 'Authorization: Bearer ***'"
  echo "curl '${base_url}/internal/api/v1/outbox/notification-channel/quarantined-events?limit=${channel_limit}' -H 'Authorization: Bearer ***'"
}

require_runtime() {
  command -v curl >/dev/null 2>&1 || { echo "curl command is required" >&2; exit 1; }
  command -v jq >/dev/null 2>&1 || { echo "jq command is required" >&2; exit 1; }
  if [[ -z "${token}" ]]; then
    echo "OUTBOX_BACKLOG_TOKEN is required for internal outbox ops HTTP gate" >&2
    exit 1
  fi
}

fetch_json() {
  local path="$1"
  local output="$2"
  curl -fsS "${base_url}${path}" \
    -H "Authorization: Bearer ${token}" \
    -o "${output}"
}

jq_number() {
  local file="$1"
  local expression="$2"
  jq -r "${expression} // 0" "${file}"
}

check_lte() {
  local metric="$1"
  local value="$2"
  local threshold="$3"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${metric} returned invalid value: ${value}" >&2
    exit 1
  fi
  if ((value > threshold)); then
    echo "${metric} exceeded threshold: value=${value} threshold=${threshold}" >&2
    exit 1
  fi
}

write_summary() {
  local lag_seconds failed_count quarantined_count stale_sending_count
  local notification_lag_count dlq_count channel_quarantined_count
  lag_seconds="$(jq_number "${outbox_json}" ".lagSeconds")"
  failed_count="$(jq_number "${outbox_json}" ".failedCount")"
  quarantined_count="$(jq_number "${outbox_json}" ".quarantinedCount")"
  stale_sending_count="$(jq_number "${outbox_json}" ".staleSendingCount")"
  notification_lag_count="$(jq_number "${notification_json}" ".lagCount")"
  dlq_count="$(jq_number "${notification_json}" ".dlqCount")"
  channel_quarantined_count="$(jq_number "${channel_json}" ".items | length")"

  {
    printf "lag_seconds\tfailed_count\tquarantined_count\tstale_sending_count\tnotification_lag_count\tdlq_count\tchannel_quarantined_count\toutbox_json\tnotification_json\tchannel_json\n"
    printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
      "${lag_seconds}" "${failed_count}" "${quarantined_count}" "${stale_sending_count}" \
      "${notification_lag_count}" "${dlq_count}" "${channel_quarantined_count}" \
      "${outbox_json}" "${notification_json}" "${channel_json}"
  } >"${summary_tsv}"

  check_lte "lagSeconds" "${lag_seconds}" "${max_lag_seconds}"
  check_lte "failedCount" "${failed_count}" "${max_failed_count}"
  check_lte "quarantinedCount" "${quarantined_count}" "${max_quarantined_count}"
  check_lte "staleSendingCount" "${stale_sending_count}" "${max_stale_sending_count}"
  check_lte "lagCount" "${notification_lag_count}" "${max_notification_lag_count}"
  check_lte "dlqCount" "${dlq_count}" "${max_dlq_count}"
  check_lte "channel_quarantined_count" "${channel_quarantined_count}" "${max_quarantined_count}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_dry_run
  exit 0
fi

require_runtime
mkdir -p "${report_dir}"
fetch_json "/internal/api/v1/outbox/summary" "${outbox_json}"
fetch_json "/internal/api/v1/outbox/notification/summary" "${notification_json}"
fetch_json "/internal/api/v1/outbox/notification-channel/quarantined-events?limit=${channel_limit}" "${channel_json}"
write_summary
echo "[outbox-provider-backlog] summary=${summary_tsv}"
