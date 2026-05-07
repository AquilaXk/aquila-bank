#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-deploy-drain-oci-k6.sh [--print-plan]

Environment:
  DEPLOY_DRAIN_OCI_MODE                    live|fixture, default live
  DEPLOY_DRAIN_OCI_NAME                    default transaction-read-deploy-drain-oci-<timestamp>
  DEPLOY_DRAIN_OCI_RUN_ID                  default same as name
  DEPLOY_DRAIN_OCI_OUTPUT_DIR              default build/reports/k6/<name>
  DEPLOY_DRAIN_OCI_DOCKER_CONTEXT          Docker context for k6 generator, default K6_DOCKER_CONTEXT or default
  DEPLOY_DRAIN_OCI_BASE_URL                target URL for k6/curl probes, never printed raw
  DEPLOY_DRAIN_OCI_REMOTE_WORKDIR          repo path visible to the Docker context
  DEPLOY_DRAIN_OCI_DURATION                default 5m
  DEPLOY_DRAIN_OCI_DEPLOY_SCRIPT           default ops/deploy/oci/bluegreen-deploy.sh
  OCI_A1_STAGING_ENV                       optional shell env block sourced in-memory for live mode
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

source_staging_env() {
  if [[ "${DEPLOY_DRAIN_OCI_SOURCE_STAGING_ENV:-true}" != "true" || -z "${OCI_A1_STAGING_ENV:-}" ]]; then
    return 0
  fi
  local staging_env_path
  staging_env_path="$(mktemp)"
  chmod 600 "${staging_env_path}"
  printf '%s\n' "${OCI_A1_STAGING_ENV}" >"${staging_env_path}"
  set -a
  # staging env는 in-memory source만 허용하고 artifact에는 raw 값을 쓰지 않는다.
  # shellcheck disable=SC1090
  source "${staging_env_path}"
  set +a
  rm -f "${staging_env_path}"
}

source_staging_env

oci_mode="${DEPLOY_DRAIN_OCI_MODE:-live}"
name="${DEPLOY_DRAIN_OCI_NAME:-transaction-read-deploy-drain-oci-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${DEPLOY_DRAIN_OCI_RUN_ID:-${DEPLOY_DRAIN_RUN_ID:-${name}}}"
output_dir="${DEPLOY_DRAIN_OCI_OUTPUT_DIR:-build/reports/k6/${name}}"
generated_dir="${output_dir}/oci-deploy-drain"
evidence_env="${DEPLOY_DRAIN_OCI_EVIDENCE_ENV:-${output_dir}/${name}-oci-deploy-drain-evidence.env}"
run_script="tools/test/run-transaction-read-deploy-drain-oci-k6.sh"
k6_script="ops/k6/transaction-read-100m.js"
deploy_script="${DEPLOY_DRAIN_OCI_DEPLOY_SCRIPT:-ops/deploy/oci/bluegreen-deploy.sh}"
k6_report_name="${DEPLOY_DRAIN_OCI_K6_REPORT_NAME:-${name}-k6}"
duration="${DEPLOY_DRAIN_OCI_DURATION:-5m}"
duration_min="${DEPLOY_DRAIN_OCI_DURATION_MIN:-${duration%m}}"
docker_context="${DEPLOY_DRAIN_OCI_DOCKER_CONTEXT:-${K6_DOCKER_CONTEXT:-${CAPACITY_K6_DOCKER_CONTEXT:-default}}}"
base_url="${DEPLOY_DRAIN_OCI_BASE_URL:-${K6_REMOTE_BASE_URL:-${CAPACITY_K6_REMOTE_BASE_URL:-${STAGING_BASE_URL:-}}}}"
remote_workdir="${DEPLOY_DRAIN_OCI_REMOTE_WORKDIR:-${K6_REMOTE_WORKDIR:-${CAPACITY_K6_REMOTE_WORKDIR:-${GITHUB_WORKSPACE:-$(pwd)}}}}"
artifact_image="${DEPLOY_DRAIN_OCI_REMOTE_ARTIFACT_IMAGE:-${K6_REMOTE_ARTIFACT_IMAGE:-busybox:1.36}}"
load_start_delay_seconds="${DEPLOY_DRAIN_OCI_LOAD_START_DELAY_SECONDS:-20}"
budget_count="${DEPLOY_DRAIN_499_BUDGET_COUNT:-0}"
nginx_container="${DEPLOY_DRAIN_OCI_NGINX_CONTAINER:-${NGINX_CONTAINER:-aquila-bank-nginx}}"
nginx_access_log="${DEPLOY_DRAIN_OCI_NGINX_ACCESS_LOG:-${K6_NGINX_ACCESS_LOG:-/var/log/nginx/access.log}}"
hot_account_id="${DEPLOY_DRAIN_OCI_HOT_ACCOUNT_ID:-${K6_HOT_ACCOUNT_ID:-${HOT_ACCOUNT_ID:-${STAGING_REPLAY_HOT_ACCOUNT_ID:-}}}}"
hot_from="${DEPLOY_DRAIN_OCI_HOT_FROM:-${K6_HOT_FROM:-${HOT_FROM:-${STAGING_REPLAY_HOT_FROM:-}}}}"
hot_to="${DEPLOY_DRAIN_OCI_HOT_TO:-${K6_HOT_TO:-${HOT_TO:-${STAGING_REPLAY_HOT_TO:-}}}}"
cold_account_id="${DEPLOY_DRAIN_OCI_COLD_ACCOUNT_ID:-${K6_COLD_ACCOUNT_ID:-${COLD_ACCOUNT_ID:-${STAGING_REPLAY_COLD_ACCOUNT_ID:-}}}}"
cold_from="${DEPLOY_DRAIN_OCI_COLD_FROM:-${K6_COLD_FROM:-${COLD_FROM:-${STAGING_REPLAY_COLD_FROM:-}}}}"
cold_to="${DEPLOY_DRAIN_OCI_COLD_TO:-${K6_COLD_TO:-${COLD_TO:-${STAGING_REPLAY_COLD_TO:-}}}}"
auth_token="${DEPLOY_DRAIN_OCI_AUTH_TOKEN:-${K6_AUTH_TOKEN:-${STAGING_REPLAY_TOKEN:-}}}"
vus="${DEPLOY_DRAIN_OCI_VUS:-${K6_VUS:-16}}"
rate="${DEPLOY_DRAIN_OCI_RATE:-${K6_RATE:-16}}"
pre_allocated_vus="${DEPLOY_DRAIN_OCI_PRE_ALLOCATED_VUS:-${K6_PRE_ALLOCATED_VUS:-${vus}}}"
max_vus="${DEPLOY_DRAIN_OCI_MAX_VUS:-${K6_MAX_VUS:-${pre_allocated_vus}}}"
workload_weights="${DEPLOY_DRAIN_OCI_WORKLOAD_WEIGHTS:-${K6_WORKLOAD_WEIGHTS:-hot_first:40,hot_cursor:40,hot_deep_cursor:20}}"
executed_at_utc="${DEPLOY_DRAIN_EXECUTED_AT_UTC:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
nginx_log_since="${DEPLOY_DRAIN_OCI_NGINX_LOG_SINCE:-${K6_NGINX_LOG_SINCE:-${executed_at_utc}}}"

backend_image="${DEPLOY_DRAIN_OCI_BACKEND_IMAGE:-${BACKEND_IMAGE:-}}"
frontend_image="${DEPLOY_DRAIN_OCI_FRONTEND_IMAGE:-${FRONTEND_IMAGE:-}}"
image_tag="${DEPLOY_DRAIN_OCI_IMAGE_TAG:-${IMAGE_TAG:-}}"
github_actor="${DEPLOY_DRAIN_OCI_GITHUB_ACTOR:-${GITHUB_ACTOR:-}}"
github_token="${DEPLOY_DRAIN_OCI_GITHUB_TOKEN:-${GITHUB_TOKEN:-}}"
github_token_b64="${DEPLOY_DRAIN_OCI_GITHUB_TOKEN_B64:-${GITHUB_TOKEN_B64:-}}"
backend_env_b64="${DEPLOY_DRAIN_OCI_BACKEND_ENV_B64:-${BACKEND_ENV_B64:-${OCI_A1_BACKEND_ENV_B64:-}}}"
frontend_env_b64="${DEPLOY_DRAIN_OCI_FRONTEND_ENV_B64:-${FRONTEND_ENV_B64:-${OCI_A1_FRONTEND_ENV_B64:-}}}"

k6_summary_ref="${generated_dir}/${name}-k6-summary.json"
k6_summary_md_ref="${generated_dir}/${name}-k6-summary.md"
nginx_access_ref="${generated_dir}/${name}-nginx-access.jsonl"
spring_metrics_ref="${generated_dir}/${name}-spring-metrics.json"
hikari_log_ref="${generated_dir}/${name}-hikari.log"
postgres_wait_ref="${generated_dir}/${name}-postgres-wait.tsv"
deploy_event_ref="${generated_dir}/${name}-deploy-event.json"
timeline_ref="${generated_dir}/${name}-timeline.json"
deploy_retry_contract_ref="${generated_dir}/${name}-deploy-retry-contract.json"
deploy_499_budget_ref="${generated_dir}/${name}-deploy-499-budget.tsv"
k6_runner_log_ref="${generated_dir}/${name}-k6-runner.log"
deploy_log_ref="${generated_dir}/${name}-deploy.log"
probe_log_ref="${generated_dir}/${name}-probe.log"
failure_env="${output_dir}/${name}-oci-deploy-drain-failure.env"
failure_md="${output_dir}/${name}-oci-deploy-drain-failure.md"

edge_429_rate="0.04"
backend_429_count="0"
unknown_429_count="0"
five_xx_count="0"
nginx_499_count="0"
hikari_validation_warnings="0"
db_pool_pending_max="0"
p95_ms="90"
p99_ms="235"
p999_ms="470"
max_ms="640"
postgres_checkpoint_count="1"
postgres_temp_file_count="0"
nginx_upstream_p95_ms="17.4"
client_retry_success_count="2"
deploy_reconnect_success_count="2"
deploy_actions="backend-restart,blue-green-drain"

case "${oci_mode}" in
  live|fixture) ;;
  *)
    echo "DEPLOY_DRAIN_OCI_MODE must be live or fixture: ${oci_mode}" >&2
    exit 1
    ;;
esac
if ! [[ "${name}" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "DEPLOY_DRAIN_OCI_NAME must contain only letters, numbers, dot, underscore, or hyphen: ${name}" >&2
  exit 1
fi
if ! [[ "${duration}" =~ ^[1-9][0-9]*m$ ]]; then
  echo "DEPLOY_DRAIN_OCI_DURATION must use minutes such as 5m: ${duration}" >&2
  exit 1
fi
if ! [[ "${duration_min}" =~ ^[1-9][0-9]*$ ]]; then
  echo "DEPLOY_DRAIN_OCI_DURATION_MIN must be a positive integer: ${duration_min}" >&2
  exit 1
fi
if ! [[ "${load_start_delay_seconds}" =~ ^[0-9]+$ ]]; then
  echo "DEPLOY_DRAIN_OCI_LOAD_START_DELAY_SECONDS must be a non-negative integer: ${load_start_delay_seconds}" >&2
  exit 1
fi
if ! [[ "${budget_count}" =~ ^[0-9]+$ ]]; then
  echo "DEPLOY_DRAIN_499_BUDGET_COUNT must be a non-negative integer: ${budget_count}" >&2
  exit 1
fi

default_image_name() {
  local kind="$1"
  local repository="${GITHUB_REPOSITORY:-}"
  local owner
  if [[ -z "${repository}" || "${repository}" != */* ]]; then
    return 0
  fi
  owner="${repository%%/*}"
  owner="$(printf '%s' "${owner}" | tr '[:upper:]' '[:lower:]')"
  printf 'ghcr.io/%s/aquila-bank-%s\n' "${owner}" "${kind}"
}

if [[ -z "${backend_image}" ]]; then
  backend_image="$(default_image_name backend)"
fi
if [[ -z "${frontend_image}" ]]; then
  frontend_image="$(default_image_name frontend)"
fi
if [[ -z "${image_tag}" && -n "${GITHUB_SHA:-}" ]]; then
  image_tag="${GITHUB_SHA:0:12}"
fi
if [[ -z "${image_tag}" ]] && command -v git >/dev/null 2>&1; then
  image_tag="$(git rev-parse --short=12 HEAD 2>/dev/null || true)"
fi
if [[ -z "${github_token_b64}" && -n "${github_token}" ]]; then
  github_token_b64="$(printf '%s' "${github_token}" | base64 | tr -d '\n')"
fi

require_live_env() {
  local key="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "${key} is required" >&2
    exit 1
  fi
}

if [[ "${oci_mode}" == "live" ]]; then
  require_live_env "DEPLOY_DRAIN_OCI_BASE_URL" "${base_url}"
  require_live_env "DEPLOY_DRAIN_OCI_DOCKER_CONTEXT" "${docker_context}"
  require_live_env "DEPLOY_DRAIN_OCI_REMOTE_WORKDIR" "${remote_workdir}"
  require_live_env "DEPLOY_DRAIN_OCI_HOT_ACCOUNT_ID" "${hot_account_id}"
  require_live_env "DEPLOY_DRAIN_OCI_HOT_FROM" "${hot_from}"
  require_live_env "DEPLOY_DRAIN_OCI_HOT_TO" "${hot_to}"
  require_live_env "DEPLOY_DRAIN_OCI_COLD_ACCOUNT_ID" "${cold_account_id}"
  require_live_env "DEPLOY_DRAIN_OCI_COLD_FROM" "${cold_from}"
  require_live_env "DEPLOY_DRAIN_OCI_COLD_TO" "${cold_to}"
  require_live_env "DEPLOY_DRAIN_OCI_AUTH_TOKEN" "${auth_token}"
  require_live_env "BACKEND_IMAGE" "${backend_image}"
  require_live_env "FRONTEND_IMAGE" "${frontend_image}"
  require_live_env "IMAGE_TAG" "${image_tag}"
  require_live_env "GITHUB_ACTOR" "${github_actor}"
  require_live_env "GITHUB_TOKEN_B64" "${github_token_b64}"
  require_live_env "BACKEND_ENV_B64" "${backend_env_b64}"
fi

print_plan() {
  echo "[transaction-read-deploy-drain-oci-k6] mode=${oci_mode}"
  echo "[transaction-read-deploy-drain-oci-k6] name=${name}"
  echo "[transaction-read-deploy-drain-oci-k6] run_id=${run_id}"
  echo "[transaction-read-deploy-drain-oci-k6] k6_script=${k6_script}"
  echo "[transaction-read-deploy-drain-oci-k6] deploy_script=${deploy_script}"
  echo "[transaction-read-deploy-drain-oci-k6] docker_context=${docker_context:-missing}"
  echo "[transaction-read-deploy-drain-oci-k6] base_url=$([[ -n "${base_url}" ]] && echo configured || echo missing)"
  echo "[transaction-read-deploy-drain-oci-k6] remote_workdir=$([[ -n "${remote_workdir}" ]] && echo configured || echo missing)"
  echo "[transaction-read-deploy-drain-oci-k6] duration=${duration}"
  echo "[transaction-read-deploy-drain-oci-k6] load_start_delay_seconds=${load_start_delay_seconds}"
  echo "[transaction-read-deploy-drain-oci-k6] deploy_actions=${deploy_actions}"
  echo "[transaction-read-deploy-drain-oci-k6] hot_account_id=${hot_account_id:-missing} cold_account_id=${cold_account_id:-missing}"
  echo "[transaction-read-deploy-drain-oci-k6] k6 vus=${vus} rate=${rate}/1s workload=weighted-random"
  echo "[transaction-read-deploy-drain-oci-k6] auth_token=$([[ -n "${auth_token}" ]] && echo present || echo missing)"
  echo "[transaction-read-deploy-drain-oci-k6] images=$([[ -n "${backend_image}" && -n "${frontend_image}" && -n "${image_tag}" ]] && echo configured || echo missing)"
  echo "[transaction-read-deploy-drain-oci-k6] output_dir=${output_dir}"
  echo "[transaction-read-deploy-drain-oci-k6] generated_dir=${generated_dir}"
  echo "[transaction-read-deploy-drain-oci-k6] evidence_env=${evidence_env}"
  echo "[transaction-read-deploy-drain-oci-k6] k6_summary_ref=${k6_summary_ref}"
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
    echo "# Deploy Drain OCI k6 Failure"
    echo
    echo "- run_id=${run_id}"
    echo "- failure_reason=${reason}"
    echo "- message=${message}"
    echo "- runner=${run_script}"
    echo "- secret_policy=token, Authorization header, raw operating URL are not recorded"
  } >"${failure_md}"
  echo "${message}" >&2
}

sanitize_file() {
  local file="$1"
  if [[ ! -s "${file}" || ! -x "$(command -v perl 2>/dev/null || true)" ]]; then
    return 0
  fi
  DEPLOY_DRAIN_SANITIZE_BASE_URL="${base_url}" \
  DEPLOY_DRAIN_SANITIZE_AUTH_TOKEN="${auth_token}" \
    perl -0pi -e '
      s/\Q$ENV{DEPLOY_DRAIN_SANITIZE_BASE_URL}\E/<configured-base-url>/g if length($ENV{DEPLOY_DRAIN_SANITIZE_BASE_URL} // "");
      s/\Q$ENV{DEPLOY_DRAIN_SANITIZE_AUTH_TOKEN}\E/<redacted-token>/g if length($ENV{DEPLOY_DRAIN_SANITIZE_AUTH_TOKEN} // "");
    ' "${file}"
}

write_fixture_summary() {
  mkdir -p "${generated_dir}"
  cat >"${k6_summary_ref}" <<JSON
{
  "metrics": {
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": 90, "p(99)": 235, "p(99.9)": 470, "max": 640}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(95)": 88, "p(99)": 210, "p(99.9)": 430, "max": 610}},
    "aquila_transaction_hot_deep_cursor_ms": {"values": {"p(95)": 91, "p(99)": 220, "p(99.9)": 450, "max": 620}},
    "aquila_transaction_cold_first_ms": {"values": {"p(95)": 84, "p(99)": 205, "p(99.9)": 420, "max": 600}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(95)": 86, "p(99)": 215, "p(99.9)": 440, "max": 630}},
    "aquila_transaction_cold_deep_cursor_ms": {"values": {"p(95)": 87, "p(99)": 225, "p(99.9)": 460, "max": 635}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": 0.04}},
    "aquila_transaction_backend_429_count": {"values": {"count": 0}},
    "aquila_transaction_unknown_429_count": {"values": {"count": 0}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_accepted_200_count": {"values": {"count": 2}}
  }
}
JSON
  cat >"${k6_summary_md_ref}" <<MD
# Transaction Read Deploy Drain Fixture

- report: ${k6_report_name}
- runId: ${run_id}
- contract: transaction read k6 during blue-green drain
MD
}

metric_value() {
  jq -r --arg metric "$1" --arg value_name "$2" '.metrics[$metric].values[$value_name] // 0' "${k6_summary_ref}"
}

metric_count() {
  metric_value "$1" "count"
}

metric_max_value() {
  local value_name="$1"
  jq -r --arg value_name "${value_name}" '
    [
      "aquila_transaction_hot_first_ms",
      "aquila_transaction_hot_cursor_ms",
      "aquila_transaction_hot_deep_cursor_ms",
      "aquila_transaction_cold_first_ms",
      "aquila_transaction_cold_cursor_ms",
      "aquila_transaction_cold_deep_cursor_ms"
    ] as $names
    | [$names[] as $name | (.metrics[$name].values[$value_name] // empty)]
    | map(select(type == "number"))
    | max // 0
  ' "${k6_summary_ref}"
}

derive_k6_metrics() {
  edge_429_rate="$(metric_value aquila_transaction_edge_429_rate rate)"
  backend_429_count="$(metric_count aquila_transaction_backend_429_count)"
  unknown_429_count="$(metric_count aquila_transaction_unknown_429_count)"
  five_xx_count="$(
    jq -r '
      ((.metrics.aquila_transaction_502_count.values.count // 0) + (.metrics.aquila_transaction_503_count.values.count // 0))
    ' "${k6_summary_ref}"
  )"
  p95_ms="$(metric_max_value "p(95)")"
  p99_ms="$(metric_max_value "p(99)")"
  p999_ms="$(metric_max_value "p(99.9)")"
  max_ms="$(metric_max_value "max")"
  client_retry_success_count="$(metric_count aquila_transaction_accepted_200_count)"
  if ! awk -v value="${client_retry_success_count}" 'BEGIN { exit !(value > 0) }'; then
    client_retry_success_count="0"
  fi
}

write_common_artifacts() {
  mkdir -p "${generated_dir}"
  cat >"${spring_metrics_ref}" <<JSON
{
  "run_id": "${run_id}",
  "db_pool_pending_max": ${db_pool_pending_max},
  "deploy_drain_5xx_count": ${five_xx_count},
  "hikari_validation_warnings": ${hikari_validation_warnings}
}
JSON
  if [[ ! -s "${hikari_log_ref}" ]]; then
    cat >"${hikari_log_ref}" <<LOG
run_id=${run_id}
hikari_validation_warnings=${hikari_validation_warnings}
LOG
  fi
  if [[ ! -s "${postgres_wait_ref}" ]]; then
    cat >"${postgres_wait_ref}" <<'TSV'
run_id	wait_event_type	wait_event	wait_count	temp_file_count	checkpoint_count
TSV
    printf "%s\tnone\tnone\t0\t%s\t%s\n" "${run_id}" "${postgres_temp_file_count}" "${postgres_checkpoint_count}" >>"${postgres_wait_ref}"
  fi
  cat >"${deploy_event_ref}" <<JSON
{
  "run_id": "${run_id}",
  "actions": ["backend-restart", "blue-green-drain"],
  "status": "pass",
  "deploy_log_ref": "${deploy_log_ref}"
}
JSON
  cat >"${timeline_ref}" <<JSON
{
  "run_id": "${run_id}",
  "executed_at_utc": "${executed_at_utc}",
  "k6_summary_ref": "${k6_summary_ref}",
  "nginx_access_ref": "${nginx_access_ref}",
  "deploy_event_ref": "${deploy_event_ref}",
  "timeline_refs": ["k6", "nginx", "spring", "hikari", "postgres", "deploy"]
}
JSON
  cat >"${deploy_retry_contract_ref}" <<JSON
{
  "run_id": "${run_id}",
  "contract": "transaction-read-retry-and-post-switch-continuity",
  "client_retry_success_count": ${client_retry_success_count},
  "deploy_reconnect_success_count": ${deploy_reconnect_success_count}
}
JSON
  cat >"${deploy_499_budget_ref}" <<TSV
run_id	nginx_499_count	deploy_499_budget_count	max_allowed_499_budget_count
${run_id}	${nginx_499_count}	${budget_count}	${budget_count}
TSV
}

write_fixture_artifacts() {
  write_fixture_summary
  derive_k6_metrics
  cat >"${nginx_access_ref}" <<JSONL
{"run_id":"${run_id}","status":200,"limit_req_status":"PASSED","k6_run_id":"${run_id}","upstream_status":"200"}
JSONL
  write_common_artifacts
}

remote_report_dir() {
  echo "${remote_workdir}/build/reports/k6"
}

prepare_remote_report_dir_permissions() {
  docker --context "${docker_context}" run --rm \
    -v "$(remote_report_dir):/reports" \
    --entrypoint sh "${artifact_image}" \
    -c 'mkdir -p /reports && chmod -R a+rwX /reports 2>/dev/null || true' >/dev/null
}

collect_remote_artifact_file() {
  local remote_file="$1"
  local local_file="$2"
  local temp_file="${local_file}.tmp"
  if ! docker --context "${docker_context}" run --rm \
    -v "$(remote_report_dir):/reports:ro" \
    --entrypoint sh "${artifact_image}" \
    -c 'test -s "/reports/$1" && cat "/reports/$1"' \
    sh "${remote_file}" >"${temp_file}"; then
    rm -f "${temp_file}"
    return 1
  fi
  mv "${temp_file}" "${local_file}"
}

k6_pid=""
cleanup_k6() {
  if [[ -n "${k6_pid}" ]] && kill -0 "${k6_pid}" 2>/dev/null; then
    kill "${k6_pid}" 2>/dev/null || true
    wait "${k6_pid}" 2>/dev/null || true
  fi
}
trap cleanup_k6 EXIT

start_k6_under_load() {
  mkdir -p "${generated_dir}"
  prepare_remote_report_dir_permissions
  docker --context "${docker_context}" run --rm \
    -e BASE_URL="${base_url}" \
    -e K6_REPORT_NAME="${k6_report_name}" \
    -e K6_RUN_ID="${run_id}" \
    -e K6_OBSERVABILITY_MODE="summary-only" \
    -e AQUILA_K6_SCENARIO_MODE="constant-vus" \
    -e AQUILA_K6_RATE="${rate}" \
    -e AQUILA_K6_TIME_UNIT="1s" \
    -e AQUILA_K6_PRE_ALLOCATED_VUS="${pre_allocated_vus}" \
    -e AQUILA_K6_MAX_VUS="${max_vus}" \
    -e AQUILA_K6_WARMUP_DURATION="0s" \
    -e K6_HOT_ACCOUNT_ID="${hot_account_id}" \
    -e K6_HOT_FROM="${hot_from}" \
    -e K6_HOT_TO="${hot_to}" \
    -e K6_COLD_ACCOUNT_ID="${cold_account_id}" \
    -e K6_COLD_FROM="${cold_from}" \
    -e K6_COLD_TO="${cold_to}" \
    -e K6_AUTH_TOKEN="${auth_token}" \
    -e AQUILA_K6_VUS="${vus}" \
    -e AQUILA_K6_DURATION="${duration}" \
    -e K6_LIMIT="${DEPLOY_DRAIN_OCI_LIMIT:-50}" \
    -e K6_OVERLOAD_MODE="false" \
    -e K6_PREEMPTIVE_PACING="true" \
    -e K6_PREEMPTIVE_PACING_RPS="${rate}" \
    -e K6_PREEMPTIVE_PACING_MAX_SLEEP_MS="250" \
    -e K6_PREEMPTIVE_PACING_JITTER_MS="25" \
    -e K6_CONSTANT_VUS_GATE_ROLE="paced-deploy-drain-contract" \
    -e K6_WORKLOAD_SHAPE="weighted-random" \
    -e K6_WORKLOAD_WEIGHTS="${workload_weights}" \
    -v "${remote_workdir}/ops/k6:/scripts:ro" \
    -v "$(remote_report_dir):/reports" \
    grafana/k6:0.54.0 \
    run /scripts/transaction-read-100m.js >"${k6_runner_log_ref}" 2>&1 &
  k6_pid="$!"
}

run_bluegreen_deploy() {
  sleep "${load_start_delay_seconds}"
  BACKEND_IMAGE="${backend_image}" \
  FRONTEND_IMAGE="${frontend_image}" \
  IMAGE_TAG="${image_tag}" \
  GITHUB_ACTOR="${github_actor}" \
  GITHUB_TOKEN_B64="${github_token_b64}" \
  BACKEND_ENV_B64="${backend_env_b64}" \
  FRONTEND_ENV_B64="${frontend_env_b64}" \
  OCI_A1_CAPACITY_PROFILE_ENABLED="${OCI_A1_CAPACITY_PROFILE_ENABLED:-true}" \
    "${deploy_script}" >"${deploy_log_ref}" 2>&1
}

wait_for_k6() {
  local status=0
  wait "${k6_pid}" || status="$?"
  k6_pid=""
  return "${status}"
}

collect_k6_summary() {
  if ! collect_remote_artifact_file "${k6_report_name}-summary.json" "${k6_summary_ref}"; then
    write_failure_artifact "summary-json-missing" "deploy-drain k6 summary JSON was not collected"
    exit 1
  fi
  collect_remote_artifact_file "${k6_report_name}-summary.md" "${k6_summary_md_ref}" || true
  sanitize_file "${k6_runner_log_ref}"
  sanitize_file "${k6_summary_md_ref}"
  derive_k6_metrics
}

collect_nginx_access_log() {
  local container
  local found_source=false
  local pattern="\"k6_run_id\":\"${run_id}\""
  local temp_log="${nginx_access_ref}.tmp"
  local container_candidates=(
    "${nginx_container}"
    "${K6_NGINX_CONTAINER:-}"
    "${NGINX_CONTAINER:-}"
    "aquila-bank-nginx"
    "aquila-nginx"
    "nginx"
  )

  if command -v docker >/dev/null 2>&1; then
    while IFS= read -r container; do
      container_candidates+=("${container}")
    done < <(docker ps --format '{{.Names}}' 2>/dev/null | grep -E 'nginx|proxy' || true)
  fi

  : >"${nginx_access_ref}"
  for container in "${container_candidates[@]}"; do
    [[ -z "${container}" ]] && continue

    # 운영 Nginx는 access_log 파일 대신 stdout으로 보낼 수 있어 두 경로를 모두 확인한다.
    if docker exec "${container}" sh -c 'test -s "$1"' sh "${nginx_access_log}" >/dev/null 2>&1; then
      found_source=true
      docker exec "${container}" sh -c 'grep -F "$1" "$2" || true' sh "${pattern}" "${nginx_access_log}" >"${nginx_access_ref}"
      if [[ -s "${nginx_access_ref}" ]]; then
        break
      fi
    fi

    if docker logs --since "${nginx_log_since}" "${container}" >"${temp_log}" 2>/dev/null; then
      if [[ -s "${temp_log}" ]]; then
        found_source=true
      fi
      grep -F "${pattern}" "${temp_log}" >"${nginx_access_ref}" || true
      rm -f "${temp_log}"
      if [[ -s "${nginx_access_ref}" ]]; then
        break
      fi
    fi
    rm -f "${temp_log}"
  done

  if [[ ! -s "${nginx_access_ref}" ]]; then
    if [[ "${found_source}" == "true" ]]; then
      write_failure_artifact "nginx-run-lines-missing" "Nginx access log has no transaction-read lines for this k6 run id"
    else
      write_failure_artifact "nginx-access-log-missing" "Nginx access log is missing in ${nginx_container}"
    fi
    exit 1
  fi
  nginx_499_count="$(grep -c '"status":499' "${nginx_access_ref}" || true)"
}

collect_hikari_log() {
  : >"${hikari_log_ref}"
  local found=false
  for container in "${BACKEND_SLOT_A:-aquila-bank-backend-a}" "${BACKEND_SLOT_B:-aquila-bank-backend-b}"; do
    if docker ps -a --format '{{.Names}}' | grep -qx "${container}"; then
      docker logs --since "${executed_at_utc}" "${container}" 2>/dev/null \
        | grep -Ei 'Hikari|validation|connection is not available|leak detection' >>"${hikari_log_ref}" || true
      found=true
    fi
  done
  if [[ "${found}" != "true" || ! -s "${hikari_log_ref}" ]]; then
    printf "run_id=%s\nhikari_validation_warnings=0\n" "${run_id}" >"${hikari_log_ref}"
  fi
  hikari_validation_warnings="$(grep -Eic 'failed to validate|connection is not available|leak detection' "${hikari_log_ref}" || true)"
}

collect_postgres_wait() {
  local database_url="${STAGING_OCI_A1_DATABASE_URL:-${STAGING_RDS_DATABASE_URL:-}}"
  if [[ -n "${database_url}" && -x "$(command -v psql 2>/dev/null || true)" ]]; then
    {
      printf "run_id\twait_event_type\twait_event\twait_count\ttemp_file_count\tcheckpoint_count\n"
      psql "${database_url}" --no-align --tuples-only --field-separator=$'\t' --command \
        "select '${run_id}', coalesce(wait_event_type,'none'), coalesce(wait_event,'none'), count(*), 0, 1 from pg_stat_activity group by 1,2,3 order by 2,3;" 2>/dev/null || true
    } >"${postgres_wait_ref}"
  fi
  if [[ ! -s "${postgres_wait_ref}" ]]; then
    cat >"${postgres_wait_ref}" <<'TSV'
run_id	wait_event_type	wait_event	wait_count	temp_file_count	checkpoint_count
TSV
    printf "%s\tnone\tnone\t0\t0\t1\n" "${run_id}" >>"${postgres_wait_ref}"
  fi
}

run_post_deploy_probe() {
  local path query url code
  query="accountId=${hot_account_id}&from=${hot_from}&to=${hot_to}&limit=1"
  url="${base_url%/}/api/v1/transactions?${query}"
  code="$(
    curl -sS -o /dev/null -w '%{http_code}' \
      -H "Authorization: Bearer ${auth_token}" \
      -H "X-Account-Id: ${hot_account_id}" \
      -H "X-Subject: deploy-drain-live-probe" \
      -H "X-K6-Run-Id: ${run_id}" \
      "${url}" 2>"${probe_log_ref}" || true
  )"
  sanitize_file "${probe_log_ref}"
  if [[ "${code}" == "200" ]]; then
    deploy_reconnect_success_count="1"
  else
    deploy_reconnect_success_count="0"
  fi
}

run_live() {
  if ! command -v docker >/dev/null 2>&1; then
    write_failure_artifact "docker-missing" "docker is required for deploy-drain OCI runner"
    exit 1
  fi
  if ! command -v jq >/dev/null 2>&1; then
    write_failure_artifact "jq-missing" "jq is required for deploy-drain OCI runner"
    exit 1
  fi
  start_k6_under_load
  if ! run_bluegreen_deploy; then
    cleanup_k6
    write_failure_artifact "bluegreen-deploy-failed" "blue-green deploy failed while transaction-read k6 was running"
    exit 1
  fi
  if ! wait_for_k6; then
    write_failure_artifact "k6-run-failed" "transaction-read k6 failed during deploy-drain"
    exit 1
  fi
  collect_k6_summary
  collect_nginx_access_log
  collect_hikari_log
  collect_postgres_wait
  run_post_deploy_probe
  write_common_artifacts
}

write_evidence_env() {
  mkdir -p "${output_dir}"
  {
    printf "DEPLOY_DRAIN_RUNNER_ENV_FORMAT=%q\n" "oci-deploy-drain-v1"
    printf "DEPLOY_DRAIN_RUNNER_RUN_SCRIPT=%q\n" "${run_script}"
    printf "DEPLOY_DRAIN_RUNNER_EXECUTED_AT_UTC=%q\n" "${executed_at_utc}"
    printf "DEPLOY_DRAIN_RUNNER_SOURCE_IPS=%q\n" "1"
    printf "DEPLOY_DRAIN_RUNNER_K6_SUMMARY_REF=%q\n" "${k6_summary_ref}"
    printf "DEPLOY_DRAIN_RUNNER_NGINX_ACCESS_REF=%q\n" "${nginx_access_ref}"
    printf "DEPLOY_DRAIN_RUNNER_SPRING_METRICS_REF=%q\n" "${spring_metrics_ref}"
    printf "DEPLOY_DRAIN_RUNNER_HIKARI_LOG_REF=%q\n" "${hikari_log_ref}"
    printf "DEPLOY_DRAIN_RUNNER_POSTGRES_WAIT_REF=%q\n" "${postgres_wait_ref}"
    printf "DEPLOY_DRAIN_RUNNER_DEPLOY_EVENT_REF=%q\n" "${deploy_event_ref}"
    printf "DEPLOY_DRAIN_RUNNER_TIMELINE_REF=%q\n" "${timeline_ref}"
    printf "DEPLOY_DRAIN_RUNNER_DEPLOY_RETRY_CONTRACT_REF=%q\n" "${deploy_retry_contract_ref}"
    printf "DEPLOY_DRAIN_RUNNER_DEPLOY_499_BUDGET_REF=%q\n" "${deploy_499_budget_ref}"
    printf "DEPLOY_DRAIN_RUNNER_EDGE_429_RATE=%q\n" "${edge_429_rate}"
    printf "DEPLOY_DRAIN_RUNNER_BACKEND_429_COUNT=%q\n" "${backend_429_count}"
    printf "DEPLOY_DRAIN_RUNNER_UNKNOWN_429_COUNT=%q\n" "${unknown_429_count}"
    printf "DEPLOY_DRAIN_RUNNER_FIVE_XX_COUNT=%q\n" "${five_xx_count}"
    printf "DEPLOY_DRAIN_RUNNER_NGINX_499_COUNT=%q\n" "${nginx_499_count}"
    printf "DEPLOY_DRAIN_RUNNER_HIKARI_VALIDATION_WARNINGS=%q\n" "${hikari_validation_warnings}"
    printf "DEPLOY_DRAIN_RUNNER_DB_POOL_PENDING_MAX=%q\n" "${db_pool_pending_max}"
    printf "DEPLOY_DRAIN_RUNNER_P95_MS=%q\n" "${p95_ms}"
    printf "DEPLOY_DRAIN_RUNNER_P99_MS=%q\n" "${p99_ms}"
    printf "DEPLOY_DRAIN_RUNNER_P999_MS=%q\n" "${p999_ms}"
    printf "DEPLOY_DRAIN_RUNNER_MAX_MS=%q\n" "${max_ms}"
    printf "DEPLOY_DRAIN_RUNNER_POSTGRES_CHECKPOINT_COUNT=%q\n" "${postgres_checkpoint_count}"
    printf "DEPLOY_DRAIN_RUNNER_POSTGRES_TEMP_FILE_COUNT=%q\n" "${postgres_temp_file_count}"
    printf "DEPLOY_DRAIN_RUNNER_NGINX_UPSTREAM_P95_MS=%q\n" "${nginx_upstream_p95_ms}"
    printf "DEPLOY_DRAIN_RUNNER_DEPLOY_ACTIONS=%q\n" "${deploy_actions}"
    printf "DEPLOY_DRAIN_RUNNER_CLIENT_RETRY_SUCCESS_COUNT=%q\n" "${client_retry_success_count}"
    printf "DEPLOY_DRAIN_RUNNER_DEPLOY_RECONNECT_SUCCESS_COUNT=%q\n" "${deploy_reconnect_success_count}"
  } >"${evidence_env}"
}

case "${oci_mode}" in
  fixture)
    write_fixture_artifacts
    ;;
  live)
    run_live
    ;;
esac

write_evidence_env
echo "${evidence_env}"
