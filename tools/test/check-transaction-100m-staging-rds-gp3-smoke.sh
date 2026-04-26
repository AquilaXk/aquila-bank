#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-100m-staging-rds-gp3-smoke.sh"
runbook="docs/transaction-100m-staging-rds-gp3-smoke.md"

echo "[transaction-100m-staging-rds] shell syntax"
bash -n "${script}"

echo "[transaction-100m-staging-rds] print plan"
plan="$(
  STAGING_RDS_BASE_URL=https://staging.example.invalid \
  STAGING_RDS_HOT_ACCOUNT_ID=910000001 \
  STAGING_RDS_COLD_ACCOUNT_ID=910000002 \
  STAGING_RDS_HOT_FROM=2026-04-01T00:00:00Z \
  STAGING_RDS_HOT_TO=2026-04-30T00:00:00Z \
  STAGING_RDS_COLD_FROM=2026-01-01T00:00:00Z \
  STAGING_RDS_COLD_TO=2026-01-31T00:00:00Z \
    "${script}" --print-plan
)"
grep -F "base_url=https://staging.example.invalid" <<<"${plan}" >/dev/null
grep -F "confirm=read-only-staging-rds required for run" <<<"${plan}" >/dev/null
grep -F "observability=summary-only" <<<"${plan}" >/dev/null
grep -F "generator=docker run grafana/k6:0.54.0" <<<"${plan}" >/dev/null
grep -F "archive_results=true" <<<"${plan}" >/dev/null

echo "[transaction-100m-staging-rds] dry-run command"
dry_run="$(
  STAGING_RDS_BASE_URL=https://staging.example.invalid \
  STAGING_RDS_HOT_ACCOUNT_ID=910000001 \
  STAGING_RDS_COLD_ACCOUNT_ID=910000002 \
  STAGING_RDS_HOT_FROM=2026-04-01T00:00:00Z \
  STAGING_RDS_HOT_TO=2026-04-30T00:00:00Z \
  STAGING_RDS_COLD_FROM=2026-01-01T00:00:00Z \
  STAGING_RDS_COLD_TO=2026-01-31T00:00:00Z \
    "${script}" --dry-run
)"
grep -F "docker run --rm" <<<"${dry_run}" >/dev/null
grep -F "BASE_URL=https://staging.example.invalid" <<<"${dry_run}" >/dev/null
grep -F "K6_OBSERVABILITY_MODE=summary-only" <<<"${dry_run}" >/dev/null
grep -F "Authorization token is passed only through env" <<<"${dry_run}" >/dev/null

echo "[transaction-100m-staging-rds] contract"
grep -F "STAGING_RDS_CONFIRM" "${script}" >/dev/null
grep -F "read-only-staging-rds" "${script}" >/dev/null
grep -F "STAGING_RDS_ALLOW_LOCAL_URL" "${script}" >/dev/null
grep -F "STAGING_RDS_REQUIRE_HTTPS" "${script}" >/dev/null
grep -F "K6_OBSERVABILITY_MODE=summary-only" "${script}" >/dev/null
grep -F "archive-k6-transaction-100m-result.sh" "${script}" >/dev/null
test -f "${runbook}"
grep -F "RDS db.t4g.small + gp3" "${runbook}" >/dev/null
grep -F "secret" "${runbook}" >/dev/null
grep -F "read-only-staging-rds" "${runbook}" >/dev/null

echo "[transaction-100m-staging-rds] invalid input fails"
if STAGING_RDS_BASE_URL=http://localhost:8080 "${script}" --print-plan >/dev/null 2>&1; then
  echo "localhost URL unexpectedly succeeded" >&2
  exit 1
fi
if STAGING_RDS_BASE_URL=http://staging.example.invalid "${script}" --print-plan >/dev/null 2>&1; then
  echo "non-HTTPS URL unexpectedly succeeded" >&2
  exit 1
fi
if STAGING_RDS_BASE_URL=https://staging.example.invalid STAGING_RDS_HOT_ACCOUNT_ID=1 "${script}" --print-plan >/dev/null 2>&1; then
  echo "missing required windows unexpectedly succeeded" >&2
  exit 1
fi
if STAGING_RDS_BASE_URL=https://staging.example.invalid STAGING_RDS_HOT_ACCOUNT_ID=1 STAGING_RDS_COLD_ACCOUNT_ID=1 STAGING_RDS_HOT_FROM=2026-04-01T00:00:00Z STAGING_RDS_HOT_TO=2026-04-30T00:00:00Z STAGING_RDS_COLD_FROM=2026-01-01T00:00:00Z STAGING_RDS_COLD_TO=2026-01-31T00:00:00Z "${script}" --print-plan >/dev/null 2>&1; then
  echo "same hot/cold account unexpectedly succeeded" >&2
  exit 1
fi
