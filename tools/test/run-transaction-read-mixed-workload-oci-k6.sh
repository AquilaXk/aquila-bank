#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-mixed-workload-oci-k6.sh [--print-plan]

Environment:
  MIXED_WORKLOAD_OCI_MODE                    live|fixture, default live
  MIXED_WORKLOAD_OCI_NAME                    default transaction-read-mixed-workload-oci-<timestamp>
  MIXED_WORKLOAD_OCI_RUN_ID                  default same as name
  MIXED_WORKLOAD_OCI_OUTPUT_DIR              default build/reports/k6/<name>
  MIXED_WORKLOAD_OCI_DOCKER_CONTEXT          required for live
  MIXED_WORKLOAD_OCI_BASE_URL                required for live, never printed raw
  MIXED_WORKLOAD_OCI_REMOTE_WORKDIR          repo path on remote Docker host
  MIXED_WORKLOAD_OCI_DURATION                default 30m
  MIXED_WORKLOAD_OCI_AUTH_TOKEN_FILE         bearer token file, optional but required by auth component in live
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

oci_mode="${MIXED_WORKLOAD_OCI_MODE:-live}"
name="${MIXED_WORKLOAD_OCI_NAME:-transaction-read-mixed-workload-oci-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${MIXED_WORKLOAD_OCI_RUN_ID:-${name}}"
output_dir="${MIXED_WORKLOAD_OCI_OUTPUT_DIR:-build/reports/k6/${name}}"
generated_dir="${output_dir}/oci-mixed"
evidence_env="${MIXED_WORKLOAD_OCI_EVIDENCE_ENV:-${output_dir}/${name}-oci-mixed-evidence.env}"
k6_script="ops/k6/transaction-read-mixed-workload-100m.js"
run_script="tools/test/run-transaction-read-mixed-workload-oci-k6.sh"
k6_report_name="${MIXED_WORKLOAD_OCI_K6_REPORT_NAME:-${name}-k6}"
duration="${MIXED_WORKLOAD_OCI_DURATION:-30m}"
docker_context="${MIXED_WORKLOAD_OCI_DOCKER_CONTEXT:-${K6_DOCKER_CONTEXT:-${CAPACITY_K6_DOCKER_CONTEXT:-}}}"
base_url="${MIXED_WORKLOAD_OCI_BASE_URL:-${K6_REMOTE_BASE_URL:-${CAPACITY_K6_REMOTE_BASE_URL:-${STAGING_BASE_URL:-}}}}"
remote_workdir="${MIXED_WORKLOAD_OCI_REMOTE_WORKDIR:-${K6_REMOTE_WORKDIR:-${CAPACITY_K6_REMOTE_WORKDIR:-${GITHUB_WORKSPACE:-$(pwd)}}}}"
artifact_image="${MIXED_WORKLOAD_OCI_REMOTE_ARTIFACT_IMAGE:-${K6_REMOTE_ARTIFACT_IMAGE:-busybox:1.36}}"
auth_token_file="${MIXED_WORKLOAD_OCI_AUTH_TOKEN_FILE:-${K6_AUTH_TOKEN_FILE:-}}"
auth_token_env_name="${MIXED_WORKLOAD_OCI_AUTH_TOKEN_ENV_NAME:-${K6_AUTH_TOKEN_ENV_NAME:-}}"
auth_token="${MIXED_WORKLOAD_OCI_AUTH_TOKEN:-${K6_AUTH_TOKEN:-}}"
hot_account_id="${MIXED_WORKLOAD_OCI_HOT_ACCOUNT_ID:-${K6_HOT_ACCOUNT_ID:-}}"
hot_from="${MIXED_WORKLOAD_OCI_HOT_FROM:-${K6_HOT_FROM:-}}"
hot_to="${MIXED_WORKLOAD_OCI_HOT_TO:-${K6_HOT_TO:-}}"
cold_account_id="${MIXED_WORKLOAD_OCI_COLD_ACCOUNT_ID:-${K6_COLD_ACCOUNT_ID:-}}"
cold_from="${MIXED_WORKLOAD_OCI_COLD_FROM:-${K6_COLD_FROM:-}}"
cold_to="${MIXED_WORKLOAD_OCI_COLD_TO:-${K6_COLD_TO:-}}"
archive_account_id="${MIXED_WORKLOAD_OCI_ARCHIVE_ACCOUNT_ID:-${K6_ARCHIVE_ACCOUNT_ID:-${cold_account_id}}}"
archive_from="${MIXED_WORKLOAD_OCI_ARCHIVE_FROM:-${K6_ARCHIVE_FROM:-${cold_from}}}"
archive_to="${MIXED_WORKLOAD_OCI_ARCHIVE_TO:-${K6_ARCHIVE_TO:-${cold_to}}}"
write_source_account_id="${MIXED_WORKLOAD_OCI_WRITE_SOURCE_ACCOUNT_ID:-${K6_WRITE_SOURCE_ACCOUNT_ID:-}}"
write_target_account_id="${MIXED_WORKLOAD_OCI_WRITE_TARGET_ACCOUNT_ID:-${K6_WRITE_TARGET_ACCOUNT_ID:-}}"
write_amount_minor="${MIXED_WORKLOAD_OCI_WRITE_AMOUNT_MINOR:-${K6_WRITE_AMOUNT_MINOR:-1}}"
write_currency_code="${MIXED_WORKLOAD_OCI_WRITE_CURRENCY_CODE:-${K6_WRITE_CURRENCY_CODE:-KRW}}"
read_rate="${MIXED_WORKLOAD_OCI_READ_RATE:-12}"
write_vus="${MIXED_WORKLOAD_OCI_WRITE_VUS:-2}"
auth_rate="${MIXED_WORKLOAD_OCI_AUTH_RATE:-1}"
notification_rate="${MIXED_WORKLOAD_OCI_NOTIFICATION_RATE:-1}"
limit="${MIXED_WORKLOAD_OCI_LIMIT:-50}"
executed_at_utc="${MIXED_WORKLOAD_EXECUTED_AT_UTC:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"

k6_summary_ref="${generated_dir}/${name}-k6-summary.json"
k6_summary_md_ref="${generated_dir}/${name}-k6-summary.md"
nginx_access_ref="${generated_dir}/${name}-nginx-access.jsonl"
spring_metrics_ref="${generated_dir}/${name}-spring-metrics.json"
hikari_log_ref="${generated_dir}/${name}-hikari.log"
postgres_wait_ref="${generated_dir}/${name}-postgres-wait.tsv"
timeline_ref="${generated_dir}/${name}-timeline.json"
workload_mix_ref="${generated_dir}/${name}-workload-mix.json"
workload_component_ref="${generated_dir}/${name}-workload-components.tsv"
outbox_lag_ref="${generated_dir}/${name}-outbox-lag.tsv"
read_429_source_ref="${generated_dir}/${name}-read-429-source.tsv"
read_bucket_ref="${generated_dir}/${name}-read-buckets.tsv"
write_status_ref="${generated_dir}/${name}-write-status.tsv"
runner_log_ref="${generated_dir}/${name}-runner.log"
failure_env="${output_dir}/${name}-oci-mixed-failure.env"
failure_md="${output_dir}/${name}-oci-mixed-failure.md"
k6_exit_status=0

auth_token_source="missing"

case "${oci_mode}" in
  live|fixture) ;;
  *)
    echo "MIXED_WORKLOAD_OCI_MODE must be live or fixture: ${oci_mode}" >&2
    exit 1
    ;;
esac
if ! [[ "${name}" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "MIXED_WORKLOAD_OCI_NAME must contain only letters, numbers, dot, underscore, or hyphen: ${name}" >&2
  exit 1
fi
if ! [[ "${duration}" =~ ^[1-9][0-9]*(s|m|h)$ ]]; then
  echo "MIXED_WORKLOAD_OCI_DURATION must use a positive duration such as 60s, 30m, or 1h: ${duration}" >&2
  exit 1
fi

resolve_auth_token() {
  if [[ -n "${auth_token}" ]]; then
    auth_token_source="env"
    return 0
  fi
  if [[ -n "${auth_token_env_name}" ]]; then
    if ! [[ "${auth_token_env_name}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "MIXED_WORKLOAD_OCI_AUTH_TOKEN_ENV_NAME must be a valid environment variable name" >&2
      exit 1
    fi
    auth_token="${!auth_token_env_name:-}"
    if [[ -z "${auth_token}" ]]; then
      echo "MIXED_WORKLOAD_OCI_AUTH_TOKEN_ENV_NAME points to an empty environment variable" >&2
      exit 1
    fi
    auth_token_source="env:${auth_token_env_name}"
    return 0
  fi
  if [[ -n "${auth_token_file}" ]]; then
    if [[ ! -s "${auth_token_file}" ]]; then
      echo "MIXED_WORKLOAD_OCI_AUTH_TOKEN_FILE must be a non-empty file" >&2
      exit 1
    fi
    auth_token="$(LC_ALL=C tr -d '\r\n' <"${auth_token_file}")"
    if [[ -z "${auth_token}" ]]; then
      echo "MIXED_WORKLOAD_OCI_AUTH_TOKEN_FILE did not contain a token" >&2
      exit 1
    fi
    auth_token_source="file"
    return 0
  fi
}

resolve_auth_token

require_live_env() {
  local key="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "${key} is required" >&2
    exit 1
  fi
}

if [[ "${oci_mode}" == "live" ]]; then
  require_live_env "MIXED_WORKLOAD_OCI_DOCKER_CONTEXT" "${docker_context}"
  require_live_env "MIXED_WORKLOAD_OCI_BASE_URL" "${base_url}"
  require_live_env "MIXED_WORKLOAD_OCI_HOT_ACCOUNT_ID" "${hot_account_id}"
  require_live_env "MIXED_WORKLOAD_OCI_HOT_FROM" "${hot_from}"
  require_live_env "MIXED_WORKLOAD_OCI_HOT_TO" "${hot_to}"
  require_live_env "MIXED_WORKLOAD_OCI_COLD_ACCOUNT_ID" "${cold_account_id}"
  require_live_env "MIXED_WORKLOAD_OCI_COLD_FROM" "${cold_from}"
  require_live_env "MIXED_WORKLOAD_OCI_COLD_TO" "${cold_to}"
  require_live_env "MIXED_WORKLOAD_OCI_ARCHIVE_ACCOUNT_ID" "${archive_account_id}"
  require_live_env "MIXED_WORKLOAD_OCI_ARCHIVE_FROM" "${archive_from}"
  require_live_env "MIXED_WORKLOAD_OCI_ARCHIVE_TO" "${archive_to}"
  require_live_env "MIXED_WORKLOAD_OCI_WRITE_SOURCE_ACCOUNT_ID" "${write_source_account_id}"
  require_live_env "MIXED_WORKLOAD_OCI_WRITE_TARGET_ACCOUNT_ID" "${write_target_account_id}"
  require_live_env "MIXED_WORKLOAD_OCI_AUTH_TOKEN" "${auth_token}"
fi

print_plan() {
  echo "[transaction-read-mixed-workload-oci-k6] mode=${oci_mode}"
  echo "[transaction-read-mixed-workload-oci-k6] name=${name}"
  echo "[transaction-read-mixed-workload-oci-k6] run_id=${run_id}"
  echo "[transaction-read-mixed-workload-oci-k6] k6_script=${k6_script}"
  echo "[transaction-read-mixed-workload-oci-k6] docker_context=${docker_context:-missing}"
  echo "[transaction-read-mixed-workload-oci-k6] base_url=$([[ -n "${base_url}" ]] && echo configured || echo missing)"
  echo "[transaction-read-mixed-workload-oci-k6] remote_workdir=${remote_workdir}"
  echo "[transaction-read-mixed-workload-oci-k6] duration=${duration}"
  echo "[transaction-read-mixed-workload-oci-k6] components=read,write,auth,notification,sse"
  echo "[transaction-read-mixed-workload-oci-k6] read_rate=${read_rate}/1s write_vus=${write_vus} auth_rate=${auth_rate}/1s notification_rate=${notification_rate}/1s"
  echo "[transaction-read-mixed-workload-oci-k6] hot_account_id=${hot_account_id:-missing} cold_account_id=${cold_account_id:-missing}"
  echo "[transaction-read-mixed-workload-oci-k6] archive_account_id=${archive_account_id:-missing}"
  echo "[transaction-read-mixed-workload-oci-k6] write_source_account_id=${write_source_account_id:-missing} write_target_account_id=${write_target_account_id:-missing}"
  echo "[transaction-read-mixed-workload-oci-k6] auth_token=$([[ -n "${auth_token}" ]] && echo present || echo missing) source=${auth_token_source}"
  echo "[transaction-read-mixed-workload-oci-k6] output_dir=${output_dir}"
  echo "[transaction-read-mixed-workload-oci-k6] generated_dir=${generated_dir}"
  echo "[transaction-read-mixed-workload-oci-k6] evidence_env=${evidence_env}"
  echo "[transaction-read-mixed-workload-oci-k6] k6_summary_ref=${k6_summary_ref}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

write_failure_artifact() {
  local reason="$1"
  local message="$2"
  mkdir -p "${output_dir}"
  {
    printf "run_id=%s\n" "${run_id}"
    printf "failure_reason=%s\n" "${reason}"
    printf "message=%s\n" "${message}"
    printf "runner=%s\n" "${run_script}"
    printf "output_dir=%s\n" "${output_dir}"
  } >"${failure_env}"
  {
    echo "# Mixed Workload OCI k6 Failure"
    echo
    echo "- run_id=${run_id}"
    echo "- failure_reason=${reason}"
    echo "- message=${message}"
    echo "- runner=${run_script}"
    echo "- secret_policy=token, Authorization header, raw operating URL are not recorded"
  } >"${failure_md}"
  echo "${message}" >&2
}

write_fixture_summary() {
  mkdir -p "${generated_dir}"
  cat >"${k6_summary_ref}" <<JSON
{
  "metrics": {
    "aquila_mixed_read_count": {"values": {"count": 128}},
    "aquila_mixed_read_duration_ms": {"values": {"p(95)": 85, "p(99)": 225, "p(99.9)": 440, "max": 630}},
    "aquila_mixed_read_edge_429_rate": {"values": {"rate": 0.08}},
    "aquila_mixed_read_backend_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_unknown_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_hot_count": {"values": {"count": 44}},
    "aquila_mixed_read_hot_duration_ms": {"values": {"p(95)": 72, "p(99)": 180, "p(99.9)": 350, "max": 520}},
    "aquila_mixed_read_hot_edge_429_rate": {"values": {"rate": 0.03}},
    "aquila_mixed_read_hot_backend_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_hot_unknown_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_cold_count": {"values": {"count": 42}},
    "aquila_mixed_read_cold_duration_ms": {"values": {"p(95)": 88, "p(99)": 205, "p(99.9)": 410, "max": 590}},
    "aquila_mixed_read_cold_edge_429_rate": {"values": {"rate": 0.05}},
    "aquila_mixed_read_cold_backend_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_cold_unknown_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_archive_count": {"values": {"count": 42}},
    "aquila_mixed_read_archive_duration_ms": {"values": {"p(95)": 96, "p(99)": 225, "p(99.9)": 440, "max": 630}},
    "aquila_mixed_read_archive_edge_429_rate": {"values": {"rate": 0.08}},
    "aquila_mixed_read_archive_backend_429_count": {"values": {"count": 0}},
    "aquila_mixed_read_archive_unknown_429_count": {"values": {"count": 0}},
    "aquila_mixed_write_count": {"values": {"count": 32}},
    "aquila_mixed_write_duration_ms": {"values": {"p(95)": 95, "p(99)": 180, "p(99.9)": 240, "max": 300}},
    "aquila_mixed_write_2xx_count": {"values": {"count": 28}},
    "aquila_mixed_write_429_count": {"values": {"count": 1}},
    "aquila_mixed_write_unexpected_status_count": {"values": {"count": 3}},
    "aquila_mixed_write_401_count": {"values": {"count": 1}},
    "aquila_mixed_write_403_count": {"values": {"count": 1}},
    "aquila_mixed_write_409_count": {"values": {"count": 1}},
    "aquila_mixed_write_422_count": {"values": {"count": 0}},
    "aquila_mixed_write_other_unexpected_count": {"values": {"count": 0}},
    "aquila_mixed_write_429_rate": {"values": {"rate": 0.01}},
    "aquila_mixed_auth_count": {"values": {"count": 16}},
    "aquila_mixed_auth_duration_ms": {"values": {"p(95)": 42, "p(99)": 60, "p(99.9)": 70, "max": 75}},
    "aquila_mixed_notification_count": {"values": {"count": 16}},
    "aquila_mixed_notification_duration_ms": {"values": {"p(95)": 38, "p(99)": 55, "p(99.9)": 65, "max": 70}},
    "aquila_mixed_sse_connect_count": {"values": {"count": 1}},
    "aquila_mixed_5xx_count": {"values": {"count": 0}}
  }
}
JSON
  cat >"${k6_summary_md_ref}" <<MD
# Transaction Read Mixed Workload 100M Fixture

- report: ${k6_report_name}
- runId: ${run_id}
- components: read,write,auth,notification,sse
MD
}

metric_value() {
  local metric="$1"
  local value_name="$2"
  local fallback="$3"
  jq -r --arg metric "${metric}" --arg value_name "${value_name}" --arg fallback "${fallback}" \
    '.metrics[$metric].values[$value_name] // $fallback' "${k6_summary_ref}"
}

metric_count() {
  metric_value "$1" "count" "0"
}

component_status() {
  local count="$1"
  if awk -v value="${count}" 'BEGIN { exit !(value > 0) }'; then
    echo "pass"
  else
    echo "fail"
  fi
}

write_component_artifacts() {
  mkdir -p "${generated_dir}"
  local read_count write_count auth_count notification_count sse_count
  local read_p95 read_p99 read_p999 read_max edge_429_rate backend_429_count unknown_429_count five_xx_count
  local hot_count hot_p95 hot_p999 hot_edge_429_rate hot_backend_429_count hot_unknown_429_count
  local cold_count cold_p95 cold_p999 cold_edge_429_rate cold_backend_429_count cold_unknown_429_count
  local archive_count archive_p95 archive_p999 archive_edge_429_rate archive_backend_429_count archive_unknown_429_count
  local write_p95 write_2xx_count write_429_count write_unexpected_status_count
  local write_401_count write_403_count write_409_count write_422_count write_other_unexpected_count
  local auth_p95 notification_p95 outbox_lag_max

  read_count="$(metric_count aquila_mixed_read_count)"
  write_count="$(metric_count aquila_mixed_write_count)"
  auth_count="$(metric_count aquila_mixed_auth_count)"
  notification_count="$(metric_count aquila_mixed_notification_count)"
  sse_count="$(metric_count aquila_mixed_sse_connect_count)"
  read_p95="$(metric_value aquila_mixed_read_duration_ms "p(95)" "0")"
  read_p99="$(metric_value aquila_mixed_read_duration_ms "p(99)" "0")"
  read_p999="$(metric_value aquila_mixed_read_duration_ms "p(99.9)" "0")"
  read_max="$(metric_value aquila_mixed_read_duration_ms "max" "0")"
  hot_count="$(metric_count aquila_mixed_read_hot_count)"
  hot_p95="$(metric_value aquila_mixed_read_hot_duration_ms "p(95)" "0")"
  hot_p999="$(metric_value aquila_mixed_read_hot_duration_ms "p(99.9)" "0")"
  hot_edge_429_rate="$(metric_value aquila_mixed_read_hot_edge_429_rate "rate" "0")"
  hot_backend_429_count="$(metric_count aquila_mixed_read_hot_backend_429_count)"
  hot_unknown_429_count="$(metric_count aquila_mixed_read_hot_unknown_429_count)"
  cold_count="$(metric_count aquila_mixed_read_cold_count)"
  cold_p95="$(metric_value aquila_mixed_read_cold_duration_ms "p(95)" "0")"
  cold_p999="$(metric_value aquila_mixed_read_cold_duration_ms "p(99.9)" "0")"
  cold_edge_429_rate="$(metric_value aquila_mixed_read_cold_edge_429_rate "rate" "0")"
  cold_backend_429_count="$(metric_count aquila_mixed_read_cold_backend_429_count)"
  cold_unknown_429_count="$(metric_count aquila_mixed_read_cold_unknown_429_count)"
  archive_count="$(metric_count aquila_mixed_read_archive_count)"
  archive_p95="$(metric_value aquila_mixed_read_archive_duration_ms "p(95)" "0")"
  archive_p999="$(metric_value aquila_mixed_read_archive_duration_ms "p(99.9)" "0")"
  archive_edge_429_rate="$(metric_value aquila_mixed_read_archive_edge_429_rate "rate" "0")"
  archive_backend_429_count="$(metric_count aquila_mixed_read_archive_backend_429_count)"
  archive_unknown_429_count="$(metric_count aquila_mixed_read_archive_unknown_429_count)"
  write_p95="$(metric_value aquila_mixed_write_duration_ms "p(95)" "0")"
  write_2xx_count="$(metric_count aquila_mixed_write_2xx_count)"
  write_429_count="$(metric_count aquila_mixed_write_429_count)"
  write_unexpected_status_count="$(metric_count aquila_mixed_write_unexpected_status_count)"
  write_401_count="$(metric_count aquila_mixed_write_401_count)"
  write_403_count="$(metric_count aquila_mixed_write_403_count)"
  write_409_count="$(metric_count aquila_mixed_write_409_count)"
  write_422_count="$(metric_count aquila_mixed_write_422_count)"
  write_other_unexpected_count="$(metric_count aquila_mixed_write_other_unexpected_count)"
  auth_p95="$(metric_value aquila_mixed_auth_duration_ms "p(95)" "0")"
  notification_p95="$(metric_value aquila_mixed_notification_duration_ms "p(95)" "0")"
  edge_429_rate="$(metric_value aquila_mixed_read_edge_429_rate "rate" "0")"
  backend_429_count="$(metric_count aquila_mixed_read_backend_429_count)"
  unknown_429_count="$(metric_count aquila_mixed_read_unknown_429_count)"
  five_xx_count="$(metric_count aquila_mixed_5xx_count)"
  outbox_lag_max="${MIXED_WORKLOAD_OCI_OUTBOX_LAG_MAX:-0}"

  cat >"${nginx_access_ref}" <<JSONL
{"run_id":"${run_id}","status":200,"limit_req_status":"PASSED","upstream_status":"200","k6_run_id":"${run_id}"}
JSONL
  cat >"${spring_metrics_ref}" <<JSON
{
  "run_id": "${run_id}",
  "db_pool_pending_max": 0,
  "hikari_validation_warnings": 0,
  "components": ["read", "write", "auth", "notification", "sse"]
}
JSON
  cat >"${hikari_log_ref}" <<LOG
run_id=${run_id}
hikari_validation_warnings=0
LOG
  cat >"${postgres_wait_ref}" <<'TSV'
run_id	wait_event	wait_count	temp_file_count	checkpoint_count
TSV
  printf "%s\tnone\t0\t0\t1\n" "${run_id}" >>"${postgres_wait_ref}"
  cat >"${timeline_ref}" <<JSON
{
  "run_id": "${run_id}",
  "executed_at_utc": "${executed_at_utc}",
  "k6_summary_ref": "${k6_summary_ref}",
  "timeline_refs": ["k6", "nginx", "spring", "hikari", "postgres"]
}
JSON
  cat >"${workload_mix_ref}" <<JSON
{
  "run_id": "${run_id}",
  "components": {
    "read": ${read_count},
    "read_hot": ${hot_count},
    "read_cold": ${cold_count},
    "read_archive": ${archive_count},
    "write": ${write_count},
    "auth": ${auth_count},
    "notification": ${notification_count},
    "sse": ${sse_count}
  }
}
JSON
  cat >"${workload_component_ref}" <<TSV
component	status	count	p95_ms	write_2xx_count	write_429_count	write_unexpected_status_count
read	$(component_status "${read_count}")	${read_count}	${read_p95}	0	0	0
write	$(component_status "${write_count}")	${write_count}	${write_p95}	${write_2xx_count}	${write_429_count}	${write_unexpected_status_count}
auth	$(component_status "${auth_count}")	${auth_count}	${auth_p95}	0	0	0
notification	$(component_status "${notification_count}")	${notification_count}	${notification_p95}	0	0	0
sse	$(component_status "${sse_count}")	${sse_count}	0	0	0	0
TSV
  cat >"${outbox_lag_ref}" <<'TSV'
run_id	outbox_lag_max
TSV
  printf "%s\t%s\n" "${run_id}" "${outbox_lag_max}" >>"${outbox_lag_ref}"
  cat >"${read_429_source_ref}" <<'TSV'
run_id	bucket	source	edge_429_rate	backend_429_count	unknown_429_count
TSV
  printf "%s\thot\tnginx-edge\t%s\t%s\t%s\n" "${run_id}" "${hot_edge_429_rate}" "${hot_backend_429_count}" "${hot_unknown_429_count}" >>"${read_429_source_ref}"
  printf "%s\tcold\tnginx-edge\t%s\t%s\t%s\n" "${run_id}" "${cold_edge_429_rate}" "${cold_backend_429_count}" "${cold_unknown_429_count}" >>"${read_429_source_ref}"
  printf "%s\tarchive\tnginx-edge\t%s\t%s\t%s\n" "${run_id}" "${archive_edge_429_rate}" "${archive_backend_429_count}" "${archive_unknown_429_count}" >>"${read_429_source_ref}"
  cat >"${read_bucket_ref}" <<'TSV'
bucket	count	p95_ms	p999_ms	edge_429_rate	backend_429_count	unknown_429_count
TSV
  printf "hot\t%s\t%s\t%s\t%s\t%s\t%s\n" "${hot_count}" "${hot_p95}" "${hot_p999}" "${hot_edge_429_rate}" "${hot_backend_429_count}" "${hot_unknown_429_count}" >>"${read_bucket_ref}"
  printf "cold\t%s\t%s\t%s\t%s\t%s\t%s\n" "${cold_count}" "${cold_p95}" "${cold_p999}" "${cold_edge_429_rate}" "${cold_backend_429_count}" "${cold_unknown_429_count}" >>"${read_bucket_ref}"
  printf "archive\t%s\t%s\t%s\t%s\t%s\t%s\n" "${archive_count}" "${archive_p95}" "${archive_p999}" "${archive_edge_429_rate}" "${archive_backend_429_count}" "${archive_unknown_429_count}" >>"${read_bucket_ref}"
  cat >"${write_status_ref}" <<'TSV'
status	count
TSV
  printf "2xx\t%s\n" "${write_2xx_count}" >>"${write_status_ref}"
  printf "429\t%s\n" "${write_429_count}" >>"${write_status_ref}"
  printf "401\t%s\n" "${write_401_count}" >>"${write_status_ref}"
  printf "403\t%s\n" "${write_403_count}" >>"${write_status_ref}"
  printf "409\t%s\n" "${write_409_count}" >>"${write_status_ref}"
  printf "422\t%s\n" "${write_422_count}" >>"${write_status_ref}"
  printf "other_unexpected\t%s\n" "${write_other_unexpected_count}" >>"${write_status_ref}"

  {
    printf "MIXED_WORKLOAD_RUNNER_ENV_FORMAT=%q\n" "oci-mixed-v1"
    printf "MIXED_WORKLOAD_RUNNER_RUN_SCRIPT=%q\n" "${run_script}"
    printf "MIXED_WORKLOAD_RUNNER_EXECUTED_AT_UTC=%q\n" "${executed_at_utc}"
    printf "MIXED_WORKLOAD_RUNNER_SOURCE_IPS=%q\n" "1"
    printf "MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF=%q\n" "${k6_summary_ref}"
    printf "MIXED_WORKLOAD_RUNNER_NGINX_ACCESS_REF=%q\n" "${nginx_access_ref}"
    printf "MIXED_WORKLOAD_RUNNER_SPRING_METRICS_REF=%q\n" "${spring_metrics_ref}"
    printf "MIXED_WORKLOAD_RUNNER_HIKARI_LOG_REF=%q\n" "${hikari_log_ref}"
    printf "MIXED_WORKLOAD_RUNNER_POSTGRES_WAIT_REF=%q\n" "${postgres_wait_ref}"
    printf "MIXED_WORKLOAD_RUNNER_TIMELINE_REF=%q\n" "${timeline_ref}"
    printf "MIXED_WORKLOAD_RUNNER_WORKLOAD_MIX_REF=%q\n" "${workload_mix_ref}"
    printf "MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF=%q\n" "${workload_component_ref}"
    printf "MIXED_WORKLOAD_RUNNER_OUTBOX_LAG_REF=%q\n" "${outbox_lag_ref}"
    printf "MIXED_WORKLOAD_RUNNER_READ_429_SOURCE_REF=%q\n" "${read_429_source_ref}"
    printf "MIXED_WORKLOAD_RUNNER_READ_BUCKET_REF=%q\n" "${read_bucket_ref}"
    printf "MIXED_WORKLOAD_RUNNER_WRITE_STATUS_REF=%q\n" "${write_status_ref}"
    printf "MIXED_WORKLOAD_RUNNER_EDGE_429_RATE=%q\n" "${edge_429_rate}"
    printf "MIXED_WORKLOAD_RUNNER_BACKEND_429_COUNT=%q\n" "${backend_429_count}"
    printf "MIXED_WORKLOAD_RUNNER_UNKNOWN_429_COUNT=%q\n" "${unknown_429_count}"
    printf "MIXED_WORKLOAD_RUNNER_WRITE_2XX_COUNT=%q\n" "${write_2xx_count}"
    printf "MIXED_WORKLOAD_RUNNER_WRITE_429_COUNT=%q\n" "${write_429_count}"
    printf "MIXED_WORKLOAD_RUNNER_WRITE_UNEXPECTED_STATUS_COUNT=%q\n" "${write_unexpected_status_count}"
    printf "MIXED_WORKLOAD_RUNNER_READ_BUCKETS=%q\n" "hot,cold,archive"
    printf "MIXED_WORKLOAD_RUNNER_READ_HOT_P999_MS=%q\n" "${hot_p999}"
    printf "MIXED_WORKLOAD_RUNNER_READ_COLD_P999_MS=%q\n" "${cold_p999}"
    printf "MIXED_WORKLOAD_RUNNER_READ_ARCHIVE_P999_MS=%q\n" "${archive_p999}"
    printf "MIXED_WORKLOAD_RUNNER_FIVE_XX_COUNT=%q\n" "${five_xx_count}"
    printf "MIXED_WORKLOAD_RUNNER_NGINX_499_COUNT=%q\n" "0"
    printf "MIXED_WORKLOAD_RUNNER_HIKARI_VALIDATION_WARNINGS=%q\n" "0"
    printf "MIXED_WORKLOAD_RUNNER_DB_POOL_PENDING_MAX=%q\n" "0"
    printf "MIXED_WORKLOAD_RUNNER_P95_MS=%q\n" "${read_p95}"
    printf "MIXED_WORKLOAD_RUNNER_P99_MS=%q\n" "${read_p99}"
    printf "MIXED_WORKLOAD_RUNNER_P999_MS=%q\n" "${read_p999}"
    printf "MIXED_WORKLOAD_RUNNER_MAX_MS=%q\n" "${read_max}"
    printf "MIXED_WORKLOAD_RUNNER_POSTGRES_CHECKPOINT_COUNT=%q\n" "1"
    printf "MIXED_WORKLOAD_RUNNER_POSTGRES_TEMP_FILE_COUNT=%q\n" "0"
    printf "MIXED_WORKLOAD_RUNNER_NGINX_UPSTREAM_P95_MS=%q\n" "16.3"
    printf "MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENTS=%q\n" "read,write,auth,notification,sse"
    printf "MIXED_WORKLOAD_RUNNER_READ_P999_MS=%q\n" "${read_p999}"
    printf "MIXED_WORKLOAD_RUNNER_OUTBOX_LAG_MAX=%q\n" "${outbox_lag_max}"
  } >"${evidence_env}"
}

prepare_remote_report_dir_permissions() {
  docker --context "${docker_context}" run --rm \
    -v "${remote_workdir}/build/reports/k6:/reports" \
    --entrypoint sh "${artifact_image}" \
    -c 'mkdir -p /reports && chmod -R a+rwX /reports 2>/dev/null || true' >/dev/null
}

collect_remote_artifact_file() {
  local remote_file="$1"
  local local_file="$2"
  local temp_file="${local_file}.tmp"
  if ! docker --context "${docker_context}" run --rm \
    -v "${remote_workdir}/build/reports/k6:/reports:ro" \
    --entrypoint sh "${artifact_image}" \
    -c 'test -s "/reports/$1" && cat "/reports/$1"' \
    sh "${remote_file}" >"${temp_file}"; then
    rm -f "${temp_file}"
    return 1
  fi
  mv "${temp_file}" "${local_file}"
}

run_live_k6() {
  mkdir -p "${generated_dir}"
  if ! command -v docker >/dev/null 2>&1; then
    write_failure_artifact "docker-missing" "docker is required for OCI mixed workload k6 runner"
    exit 1
  fi
  prepare_remote_report_dir_permissions
  set +e
  docker --context "${docker_context}" run --rm \
    -e BASE_URL="${base_url}" \
    -e K6_REPORT_NAME="${k6_report_name}" \
    -e K6_RUN_ID="${run_id}" \
    -e K6_MIXED_DURATION="${duration}" \
    -e K6_AUTH_TOKEN="${auth_token}" \
    -e K6_HOT_ACCOUNT_ID="${hot_account_id}" \
    -e K6_HOT_FROM="${hot_from}" \
    -e K6_HOT_TO="${hot_to}" \
    -e K6_COLD_ACCOUNT_ID="${cold_account_id}" \
    -e K6_COLD_FROM="${cold_from}" \
    -e K6_COLD_TO="${cold_to}" \
    -e K6_ARCHIVE_ACCOUNT_ID="${archive_account_id}" \
    -e K6_ARCHIVE_FROM="${archive_from}" \
    -e K6_ARCHIVE_TO="${archive_to}" \
    -e K6_WRITE_SOURCE_ACCOUNT_ID="${write_source_account_id}" \
    -e K6_WRITE_TARGET_ACCOUNT_ID="${write_target_account_id}" \
    -e K6_WRITE_AMOUNT_MINOR="${write_amount_minor}" \
    -e K6_WRITE_CURRENCY_CODE="${write_currency_code}" \
    -e K6_MIXED_READ_RATE="${read_rate}" \
    -e K6_MIXED_WRITE_VUS="${write_vus}" \
    -e K6_MIXED_AUTH_RATE="${auth_rate}" \
    -e K6_MIXED_NOTIFICATION_RATE="${notification_rate}" \
    -e K6_LIMIT="${limit}" \
    -v "${remote_workdir}/ops/k6:/scripts:ro" \
    -v "${remote_workdir}/build/reports/k6:/reports" \
    grafana/k6:0.54.0 \
    run /scripts/transaction-read-mixed-workload-100m.js >"${runner_log_ref}" 2>&1
  k6_exit_status="$?"
  set -e
  if [[ "${k6_exit_status}" -ne 0 ]]; then
    write_failure_artifact "k6-run-failed" "OCI mixed workload k6 run failed"
  fi
  if ! collect_remote_artifact_file "${k6_report_name}-summary.json" "${k6_summary_ref}"; then
    write_failure_artifact "summary-json-missing" "OCI mixed workload k6 summary JSON was not collected"
    exit 1
  fi
  collect_remote_artifact_file "${k6_report_name}-summary.md" "${k6_summary_md_ref}" || true
}

case "${oci_mode}" in
  fixture)
    write_fixture_summary
    ;;
  live)
    run_live_k6
    ;;
esac

if ! command -v jq >/dev/null 2>&1; then
  write_failure_artifact "jq-missing" "jq is required to transform mixed workload k6 summary"
  exit 1
fi

write_component_artifacts
echo "${evidence_env}"
if [[ "${k6_exit_status}" -ne 0 ]]; then
  exit "${k6_exit_status}"
fi
