#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
usage: tools/ops/bootstrap-production-github-environment.sh [--env-file PATH] [--repo OWNER/REPO] [--environment production] [--vars-only] [--dry-run] [--print-plan]

Creates/updates the GitHub Environment and writes production secrets/variables from a local env file.
Real secret values must stay outside git.
USAGE
}

repo="${GITHUB_REPOSITORY:-AquilaXk/aquila-bank}"
environment="production"
env_file="tools/ops/production-github-environment.env"
dry_run=false
print_plan=false
vars_only=false

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --env-file)
      env_file="$2"
      shift
      ;;
    --repo)
      repo="$2"
      shift
      ;;
    --environment)
      environment="$2"
      shift
      ;;
    --dry-run)
      dry_run=true
      ;;
    --vars-only)
      vars_only=true
      ;;
    --print-plan)
      print_plan=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 1
      ;;
  esac
  shift
done

required_secrets=(
  ALERTMANAGER_RECEIVER_SLACK_ENABLED
  ALERTMANAGER_RECEIVER_PAGERDUTY_ENABLED
  ALERTMANAGER_RECEIVER_WEBHOOK_ENABLED
  ALERTMANAGER_RECEIVER_WEBHOOK_URL
  PRODUCTION_DEPLOY_WEBHOOK_URL
  PRODUCTION_DEPLOY_TOKEN
  PRODUCTION_BASE_URL
  PRODUCTION_SMOKE_SHA_PATH
  PRODUCTION_SMOKE_HEALTH_PATH
  PRODUCTION_SMOKE_READ_PATH
  PRODUCTION_SMOKE_WRITE_PATH
  PRODUCTION_SMOKE_WRITE_METHOD
  PRODUCTION_SMOKE_WRITE_BODY
  PRODUCTION_SMOKE_TIMEOUT_SECONDS
)

optional_secrets=(
  ALERTMANAGER_RECEIVER_SLACK_WEBHOOK_URL
  ALERTMANAGER_RECEIVER_PAGERDUTY_ROUTING_KEY
  REDIS_HOST
  TRANSACTION_READ_REPLICA_URL
  TRANSACTION_READ_REPLICA_USERNAME
  TRANSACTION_READ_REPLICA_PASSWORD
  OUTBOX_KAFKA_BOOTSTRAP_SERVERS
  NOTIFICATION_INBOX_CONSUMER_BOOTSTRAP_SERVERS
  PRODUCTION_SMOKE_AUTH_HEADER_NAME
  PRODUCTION_SMOKE_AUTH_HEADER_VALUE
  PRODUCTION_ROLLBACK_WEBHOOK_URL
  PRODUCTION_ROLLBACK_TOKEN
  PRODUCTION_ROLLBACK_TARGET_SHA
)

environment_vars=(
  SECURITY_LOGIN_THROTTLING_STORE
  SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS
  REDIS_PORT
  TRANSACTION_READ_REPLICA_ENABLED
  TRANSACTION_READ_REPLICA_POOL_MAX_SIZE
  TRANSACTION_READ_REPLICA_LAG_THRESHOLD_MS
  OUTBOX_KAFKA_ENABLED
  OUTBOX_KAFKA_TOPIC_DEFAULT
  OUTBOX_KAFKA_TOPIC_TRANSFER_BOOKED
  OUTBOX_KAFKA_TOPIC_TRANSFER_REVERSED
  OUTBOX_KAFKA_PRODUCER_ACKS
  OUTBOX_KAFKA_PRODUCER_ENABLE_IDEMPOTENCE
  OUTBOX_KAFKA_PRODUCER_MAX_IN_FLIGHT
  NOTIFICATION_INBOX_CONSUMER_ENABLED
  NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED
  NOTIFICATION_INBOX_CONSUMER_CONCURRENCY
  NOTIFICATION_INBOX_CONSUMER_TRANSFER_BOOKED_TOPIC
  NOTIFICATION_INBOX_CONSUMER_TRANSFER_REVERSED_TOPIC
  NOTIFICATION_INBOX_CONSUMER_DLQ_TOPIC
  KAFKA_TOPIC_PROVISIONING_ENABLED
  KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED
  KAFKA_TOPIC_PROVISIONING_PARTITIONS
  KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR
  KAFKA_TOPIC_PROVISIONING_MIN_IN_SYNC_REPLICAS
  OPS_API_ADMISSION_CONTROL_ENABLED
  OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX
  OPS_API_ADMISSION_CONTROL_ACCOUNT_READ_MAX
  OPS_API_ADMISSION_CONTROL_TRANSFER_WRITE_MAX
  OPS_API_ADMISSION_CONTROL_NOTIFICATION_STREAM_MAX
  OPS_API_ADMISSION_CONTROL_NOTIFICATION_READ_MAX
  OPS_API_ADMISSION_CONTROL_INTERNAL_OPS_MAX
  OPS_T3MICRO_SATURATION_GUARD_ENABLED
  OPS_T3MICRO_SATURATION_GUARD_POOL_ACTIVE_THRESHOLD_PERCENT
  OPS_T3MICRO_SATURATION_GUARD_THREADS_BUSY_THRESHOLD_PERCENT
  OPS_T3MICRO_SATURATION_GUARD_QUERY_TIMEOUT_THRESHOLD
  DB_POOL_MAX_SIZE
  SERVER_THREADS_MAX
  NOTIFICATION_SSE_MAX_TOTAL_SESSIONS
)

print_name_plan() {
  echo "[production-env-bootstrap] repo=${repo} environment=${environment}"
  echo "[production-env-bootstrap] required secrets:"
  printf '%s\n' "${required_secrets[@]}"
  echo "[production-env-bootstrap] optional secrets:"
  printf '%s\n' "${optional_secrets[@]}"
  echo "[production-env-bootstrap] variables:"
  printf '%s\n' "${environment_vars[@]}"
}

is_placeholder() {
  [[ "${1:-}" =~ ^\<.*\>$ ]]
}

value_of() {
  local name="$1"
  printf '%s' "${!name:-}"
}

require_value() {
  local name="$1"
  local value
  value="$(value_of "$name")"
  if [[ -z "${value}" ]] || is_placeholder "${value}"; then
    echo "::error::${name} must be set in ${env_file} before writing production ${environment}."
    exit 1
  fi
}

if [[ "${print_plan}" == "true" ]]; then
  print_name_plan
  exit 0
fi

if [[ ! -f "${env_file}" ]]; then
  echo "::error::Missing env file: ${env_file}. Copy tools/ops/production-github-environment.env.example first."
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${env_file}"
set +a

validation_names=("${environment_vars[@]}")
if [[ "${vars_only}" != "true" ]]; then
  validation_names=("${required_secrets[@]}" "${environment_vars[@]}")
fi

for name in "${validation_names[@]}"; do
  require_value "${name}"
done

echo "[production-env-bootstrap] target repo=${repo} environment=${environment}"
echo "[production-env-bootstrap] secrets=${#required_secrets[@]} optional_secrets=${#optional_secrets[@]} vars=${#environment_vars[@]}"
echo "[production-env-bootstrap] vars_only=${vars_only}"

if [[ "${dry_run}" == "true" ]]; then
  echo "[production-env-bootstrap] dry-run: no GitHub writes"
  exit 0
fi

gh api --method PUT "repos/${repo}/environments/${environment}" -F wait_timer=0 >/dev/null

if [[ "${vars_only}" != "true" ]]; then
  for name in "${required_secrets[@]}" "${optional_secrets[@]}"; do
    value="$(value_of "${name}")"
    if [[ -z "${value}" ]] || is_placeholder "${value}"; then
      continue
    fi
    gh secret set "${name}" --repo "${repo}" --env "${environment}" --body "${value}" >/dev/null
    echo "[production-env-bootstrap] secret set: ${name}"
  done
fi

for name in "${environment_vars[@]}"; do
  value="$(value_of "${name}")"
  gh variable set "${name}" --repo "${repo}" --env "${environment}" --body "${value}" >/dev/null
  echo "[production-env-bootstrap] variable set: ${name}"
done

echo "[production-env-bootstrap] completed"
