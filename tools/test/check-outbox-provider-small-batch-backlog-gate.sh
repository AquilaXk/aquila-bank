#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-outbox-provider-small-batch-backlog-gate.sh"
local_script="tools/test/run-outbox-provider-backlog-local-gate.sh"
token_script="tools/test/issue-internal-service-token.sh"

echo "[outbox-provider-backlog] shell syntax"
bash -n "${script}"
bash -n "${local_script}"
bash -n "${token_script}"

echo "[outbox-provider-backlog] print plan"
plan="$(
  OUTBOX_BACKLOG_NAME=outbox-backlog-check \
  OUTBOX_BACKLOG_BASE_URL=http://localhost:8080 \
  OUTBOX_BACKLOG_MAX_LAG_SECONDS=30 \
    "${script}" --print-plan
)"
grep -F "name=outbox-backlog-check" <<<"${plan}" >/dev/null
grep -F "base_url=http://localhost:8080" <<<"${plan}" >/dev/null
grep -F "max_lag_seconds=30" <<<"${plan}" >/dev/null
grep -F "summary=build/reports/outbox/outbox-backlog-check/outbox-provider-backlog-summary.tsv" <<<"${plan}" >/dev/null

echo "[outbox-provider-backlog] dry-run command"
dry_run="$("${script}" --dry-run)"
grep -F "/internal/api/v1/outbox/summary" <<<"${dry_run}" >/dev/null
grep -F "/internal/api/v1/outbox/notification/summary" <<<"${dry_run}" >/dev/null
grep -F "/internal/api/v1/outbox/notification-channel/quarantined-events" <<<"${dry_run}" >/dev/null
grep -F "Authorization: Bearer ***" <<<"${dry_run}" >/dev/null

echo "[outbox-provider-backlog] contract"
grep -F "OUTBOX_BACKLOG_TOKEN" "${script}" >/dev/null
grep -F "outbox-provider-backlog-summary.tsv" "${script}" >/dev/null
grep -F "failedCount" "${script}" >/dev/null
grep -F "quarantinedCount" "${script}" >/dev/null
grep -F "staleSendingCount" "${script}" >/dev/null
grep -F "lagCount" "${script}" >/dev/null
grep -F "dlqCount" "${script}" >/dev/null
grep -F "channel_quarantined_count" "${script}" >/dev/null
grep -F "OUTBOX_OPS_ENABLED" compose.loadtest.yml >/dev/null
grep -F "NOTIFICATION_CHANNEL_PROVIDER_OPS_ENABLED" compose.loadtest.yml >/dev/null

echo "[outbox-provider-backlog] local launcher plan"
local_plan="$(
  OUTBOX_BACKLOG_NAME=outbox-backlog-local-check \
    "${local_script}" --print-plan
)"
grep -F "mode=print-plan" <<<"${local_plan}" >/dev/null
grep -F "token_mode=generate" <<<"${local_plan}" >/dev/null
grep -F "gate=tools/test/run-outbox-provider-small-batch-backlog-gate.sh" <<<"${local_plan}" >/dev/null

echo "[outbox-provider-backlog] local launcher dry-run"
local_dry_run="$("${local_script}" --dry-run)"
grep -F "OUTBOX_OPS_ENABLED=true" <<<"${local_dry_run}" >/dev/null
grep -F "issue-internal-service-token.sh internal:outbox-ops" <<<"${local_dry_run}" >/dev/null
grep -F "tools/test/run-outbox-provider-small-batch-backlog-gate.sh" <<<"${local_dry_run}" >/dev/null

echo "[outbox-provider-backlog] invalid input fails"
if OUTBOX_BACKLOG_MAX_LAG_SECONDS=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad OUTBOX_BACKLOG_MAX_LAG_SECONDS unexpectedly succeeded" >&2
  exit 1
fi
if OUTBOX_BACKLOG_MAX_FAILED_COUNT=-1 "${script}" --print-plan >/dev/null 2>&1; then
  echo "negative OUTBOX_BACKLOG_MAX_FAILED_COUNT unexpectedly succeeded" >&2
  exit 1
fi
if OUTBOX_BACKLOG_CHANNEL_LIMIT=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "OUTBOX_BACKLOG_CHANNEL_LIMIT=0 unexpectedly succeeded" >&2
  exit 1
fi
if OUTBOX_LOCAL_TOKEN_MODE=bad "${local_script}" --print-plan >/dev/null 2>&1; then
  echo "OUTBOX_LOCAL_TOKEN_MODE=bad unexpectedly succeeded" >&2
  exit 1
fi
