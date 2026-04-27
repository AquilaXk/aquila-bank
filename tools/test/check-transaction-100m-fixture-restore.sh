#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-100m-fixture-restore.sh"

echo "[transaction-fixture-restore] shell syntax"
bash -n "${script}"

echo "[transaction-fixture-restore] compose config"
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml config >/dev/null

echo "[transaction-fixture-restore] print plan"
plan="$(
  FIXTURE_MODE=verify \
  FIXTURE_NAME=transaction-100m-check \
  FIXTURE_POSTGRES_CONTAINER_NAME=transaction-100m-postgres-check \
  FIXTURE_REQUIRE_DUMP=true \
  FIXTURE_VERIFY_MIN_ROWS=1000 \
    "${script}" --print-plan
)"
grep -F "fixture=transaction-100m-check" <<<"${plan}" >/dev/null
grep -F "mode=verify" <<<"${plan}" >/dev/null
grep -F "dump=build/fixtures/transaction-100m-check.dump" <<<"${plan}" >/dev/null
grep -F "recovery_preflight=true" <<<"${plan}" >/dev/null
grep -F "postgres_container=transaction-100m-postgres-check" <<<"${plan}" >/dev/null
grep -F "require_dump=true" <<<"${plan}" >/dev/null
grep -F "verify_min_rows=1000" <<<"${plan}" >/dev/null
grep -F "modes=verify,dump,restore" <<<"${plan}" >/dev/null

echo "[transaction-fixture-restore] runner contract"
grep -F "FIXTURE_RECOVERY_PREFLIGHT" "${script}" >/dev/null
grep -F "FIXTURE_REQUIRE_DUMP" "${script}" >/dev/null
grep -F "FIXTURE_VERIFY_MIN_ROWS" "${script}" >/dev/null
grep -F "assert_postgres_recovery_safe" "${script}" >/dev/null
grep -F "assert_fixture_dump_present" "${script}" >/dev/null
grep -F "docker cp \"\${postgres_container_name}:" "${script}" >/dev/null
grep -F "docker cp \"\${fixture_path}\" \"\${postgres_container_name}:" "${script}" >/dev/null
if grep -F "docker cp \"aquila-bank-postgres:" "${script}" >/dev/null; then
  echo "docker cp still hard-codes aquila-bank-postgres" >&2
  exit 1
fi
grep -F "OOMKilled" "${script}" >/dev/null
grep -F "Restarting" "${script}" >/dev/null
grep -F "pg_is_in_recovery()" "${script}" >/dev/null
grep -F "fixture dump not found" "${script}" >/dev/null
grep -F "transaction_read_model rows" "${script}" >/dev/null

echo "[transaction-fixture-restore] invalid input fails"
if FIXTURE_MODE=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "FIXTURE_MODE=bad unexpectedly succeeded" >&2
  exit 1
fi
if FIXTURE_RECOVERY_PREFLIGHT=maybe "${script}" --print-plan >/dev/null 2>&1; then
  echo "FIXTURE_RECOVERY_PREFLIGHT=maybe unexpectedly succeeded" >&2
  exit 1
fi
if FIXTURE_REQUIRE_DUMP=maybe "${script}" --print-plan >/dev/null 2>&1; then
  echo "FIXTURE_REQUIRE_DUMP=maybe unexpectedly succeeded" >&2
  exit 1
fi
if FIXTURE_VERIFY_MIN_ROWS=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "FIXTURE_VERIFY_MIN_ROWS=bad unexpectedly succeeded" >&2
  exit 1
fi
