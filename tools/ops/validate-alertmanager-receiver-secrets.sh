#!/usr/bin/env bash
set -euo pipefail

environment_name="${ALERTMANAGER_RECEIVER_SECRET_SMOKE_ENVIRONMENT:-unknown}"

fail() {
  echo "[alertmanager-secret-smoke] ${1}" >&2
  exit 1
}

normalize_flag() {
  case "${1:-}" in
    true | TRUE | 1 | yes | YES | on | ON) echo "true" ;;
    false | FALSE | 0 | no | NO | off | OFF | "") echo "false" ;;
    *) echo "invalid" ;;
  esac
}

require_flag() {
  local name="$1"
  local value
  value="$(normalize_flag "${!name:-}")"
  if [[ "${value}" == "invalid" ]]; then
    fail "${name} must be one of true/false/1/0/yes/no/on/off"
  fi
  echo "${value}"
}

require_secret_when_enabled() {
  local enabled="$1"
  local receiver_name="$2"
  local secret_name="$3"
  local secret_value="$4"

  if [[ "${enabled}" == "true" && -z "${secret_value}" ]]; then
    fail "${receiver_name} receiver is enabled but ${secret_name} is missing"
  fi
}

slack_enabled="$(require_flag ALERTMANAGER_RECEIVER_SLACK_ENABLED)"
pagerduty_enabled="$(require_flag ALERTMANAGER_RECEIVER_PAGERDUTY_ENABLED)"
webhook_enabled="$(require_flag ALERTMANAGER_RECEIVER_WEBHOOK_ENABLED)"

enabled_count=0
[[ "${slack_enabled}" == "true" ]] && enabled_count=$((enabled_count + 1))
[[ "${pagerduty_enabled}" == "true" ]] && enabled_count=$((enabled_count + 1))
[[ "${webhook_enabled}" == "true" ]] && enabled_count=$((enabled_count + 1))

if [[ "${enabled_count}" -eq 0 ]]; then
  fail "at least one real Alertmanager receiver must be enabled for ${environment_name}"
fi

require_secret_when_enabled \
  "${slack_enabled}" \
  "Slack" \
  "ALERTMANAGER_RECEIVER_SLACK_WEBHOOK_URL" \
  "${ALERTMANAGER_RECEIVER_SLACK_WEBHOOK_URL:-}"
require_secret_when_enabled \
  "${pagerduty_enabled}" \
  "PagerDuty" \
  "ALERTMANAGER_RECEIVER_PAGERDUTY_ROUTING_KEY" \
  "${ALERTMANAGER_RECEIVER_PAGERDUTY_ROUTING_KEY:-}"
require_secret_when_enabled \
  "${webhook_enabled}" \
  "Webhook" \
  "ALERTMANAGER_RECEIVER_WEBHOOK_URL" \
  "${ALERTMANAGER_RECEIVER_WEBHOOK_URL:-}"

echo "[alertmanager-secret-smoke] environment=${environment_name} slack=${slack_enabled} pagerduty=${pagerduty_enabled} webhook=${webhook_enabled}"
echo "[alertmanager-secret-smoke] passed"
