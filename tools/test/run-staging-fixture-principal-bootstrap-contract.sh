#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/staging-fixture-principal-bootstrap.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

psql_log="${tmp_dir}/psql.log"
psql_stdin_log="${tmp_dir}/psql.stdin.sql"
cat >"${tmp_dir}/psql" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${PSQL_STUB_LOG}"
for arg in "$@"; do
  if [[ "$arg" == "--command" || "$arg" == "-c" ]]; then
    echo "fixture SQL must be passed through stdin so psql variables are substituted" >&2
    exit 64
  fi
done
cat >"${PSQL_STDIN_LOG}"
SH
chmod +x "${tmp_dir}/psql"

run_bootstrap() {
  env \
    PATH="${tmp_dir}:${PATH}" \
    PSQL_STUB_LOG="${psql_log}" \
    PSQL_STDIN_LOG="${psql_stdin_log}" \
    STAGING_OCI_A1_DATABASE_URL="postgresql://fixture-db/aquila" \
    STAGING_REPLAY_USER_ID="55" \
    STAGING_REPLAY_LOGIN_ID="staging-fixture-user" \
    STAGING_REPLAY_USER_PASSWORD_HASH="fixturePasswordHashWithLetters" \
    STAGING_REPLAY_USER_DISPLAY_NAME="Staging Fixture User" \
    HOT_ACCOUNT_ID="1001" \
    COLD_ACCOUNT_ID="1002" \
    "${script}"
}

assert_valid_secret_like_values_do_not_enter_arithmetic() {
  run_bootstrap >/dev/null
  grep -q -- "fixture_password_hash=fixturePasswordHashWithLetters" "${psql_log}"
}

assert_too_long_password_hash_fails_before_psql() {
  local log="${tmp_dir}/too-long.log"
  local long_hash
  long_hash="$(printf '%0121d' 0)"
  : >"${psql_log}"

  if env \
    PATH="${tmp_dir}:${PATH}" \
    PSQL_STUB_LOG="${psql_log}" \
    PSQL_STDIN_LOG="${psql_stdin_log}" \
    STAGING_OCI_A1_DATABASE_URL="postgresql://fixture-db/aquila" \
    STAGING_REPLAY_USER_ID="55" \
    STAGING_REPLAY_LOGIN_ID="staging-fixture-user" \
    STAGING_REPLAY_USER_PASSWORD_HASH="${long_hash}" \
    STAGING_REPLAY_USER_DISPLAY_NAME="Staging Fixture User" \
    HOT_ACCOUNT_ID="1001" \
    COLD_ACCOUNT_ID="1002" \
    "${script}" >"${log}" 2>&1; then
    echo "expected too long password hash to fail" >&2
    exit 1
  fi

  grep -q -- "STAGING_REPLAY_USER_PASSWORD_HASH must be 120 characters or less" "${log}"
  if [ -s "${psql_log}" ]; then
    echo "expected validation failure before psql execution" >&2
    exit 1
  fi
}

assert_valid_secret_like_values_do_not_enter_arithmetic
assert_too_long_password_hash_fails_before_psql

echo "[staging-fixture-principal-contract] ok"
