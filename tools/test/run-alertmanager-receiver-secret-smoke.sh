#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/validate-alertmanager-receiver-secrets.sh"

expect_failure() {
  local expected="$1"
  shift

  local output
  if output="$("$@" 2>&1)"; then
    echo "[alertmanager-secret-smoke-test] expected failure but command passed: $*" >&2
    exit 1
  fi

  if [[ "${output}" != *"${expected}"* ]]; then
    echo "[alertmanager-secret-smoke-test] missing expected message: ${expected}" >&2
    echo "${output}" >&2
    exit 1
  fi
}

echo "[alertmanager-secret-smoke-test] bash syntax"
bash -n "${script}"

echo "[alertmanager-secret-smoke-test] fails when no receiver is enabled"
expect_failure \
  "at least one real Alertmanager receiver must be enabled" \
  env ALERTMANAGER_RECEIVER_SECRET_SMOKE_ENVIRONMENT=staging \
    ALERTMANAGER_RECEIVER_SLACK_ENABLED=false \
    ALERTMANAGER_RECEIVER_PAGERDUTY_ENABLED=false \
    ALERTMANAGER_RECEIVER_WEBHOOK_ENABLED=false \
    bash "${script}"

echo "[alertmanager-secret-smoke-test] fails when enabled receiver secret is missing"
expect_failure \
  "Slack receiver is enabled but ALERTMANAGER_RECEIVER_SLACK_WEBHOOK_URL is missing" \
  env ALERTMANAGER_RECEIVER_SECRET_SMOKE_ENVIRONMENT=staging \
    ALERTMANAGER_RECEIVER_SLACK_ENABLED=true \
    ALERTMANAGER_RECEIVER_PAGERDUTY_ENABLED=false \
    ALERTMANAGER_RECEIVER_WEBHOOK_ENABLED=false \
    bash "${script}"

echo "[alertmanager-secret-smoke-test] passes with one configured receiver"
env \
  ALERTMANAGER_RECEIVER_SECRET_SMOKE_ENVIRONMENT=staging \
  ALERTMANAGER_RECEIVER_SLACK_ENABLED=true \
  ALERTMANAGER_RECEIVER_SLACK_WEBHOOK_URL=https://hooks.slack.example/services/test \
  ALERTMANAGER_RECEIVER_PAGERDUTY_ENABLED=false \
  ALERTMANAGER_RECEIVER_WEBHOOK_ENABLED=false \
  bash "${script}"

echo "[alertmanager-secret-smoke-test] passes with multiple configured receivers"
env \
  ALERTMANAGER_RECEIVER_SECRET_SMOKE_ENVIRONMENT=production \
  ALERTMANAGER_RECEIVER_SLACK_ENABLED=true \
  ALERTMANAGER_RECEIVER_SLACK_WEBHOOK_URL=https://hooks.slack.example/services/test \
  ALERTMANAGER_RECEIVER_PAGERDUTY_ENABLED=true \
  ALERTMANAGER_RECEIVER_PAGERDUTY_ROUTING_KEY=prod-routing-key \
  ALERTMANAGER_RECEIVER_WEBHOOK_ENABLED=true \
  ALERTMANAGER_RECEIVER_WEBHOOK_URL=https://alerts.example/internal \
  bash "${script}"

echo "[alertmanager-secret-smoke-test] passed"
