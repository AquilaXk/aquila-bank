#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-query-plan-regression-gate.sh"

echo "[transaction-plan-gate] shell syntax"
bash -n "${runner}"

echo "[transaction-plan-gate] print plan"
plan="$("${runner}" --print-plan)"
grep -F "fixture=baseline hot account + long-history partition-fit + concurrency SLO" <<<"${plan}" >/dev/null
grep -F "filter_matrix=first_page,cursor,deep_cursor,status_cursor,direction_first,amount_first,mixed_cursor,reference_exact" <<<"${plan}" >/dev/null
grep -F "mixed_cursor=status+direction+amount+cursor" <<<"${plan}" >/dev/null
grep -F "expectation=no Seq Scan or Sort on account-scoped keyset plans" <<<"${plan}" >/dev/null
grep -F "gradle_task=./back/gradlew -p back queryPlanTest" <<<"${plan}" >/dev/null
