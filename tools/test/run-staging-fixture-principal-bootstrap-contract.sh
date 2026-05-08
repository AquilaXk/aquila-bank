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
count_file="${PSQL_CALL_COUNT_FILE:-}"
if [[ -n "${count_file}" ]]; then
  count=0
  if [[ -f "${count_file}" ]]; then
    count="$(cat "${count_file}")"
  fi
  count=$((count + 1))
  echo "${count}" >"${count_file}"
  if [[ "${PSQL_FAIL_FIRST:-false}" == "true" && "${count}" -eq 1 ]]; then
    echo 'psql: error: connection to server at "146.56.149.120", port 5432 failed: Connection timed out' >&2
    exit 2
  fi
fi
printf '%s\n' "$*" >>"${PSQL_STUB_LOG}"
for arg in "$@"; do
  if [[ "$arg" == "--command" || "$arg" == "-c" ]]; then
    echo "fixture SQL must be passed through stdin so psql variables are substituted" >&2
    exit 64
  fi
done
cat >>"${PSQL_STDIN_LOG}"
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

run_bootstrap_with_csv_accounts() {
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
    HOT_ACCOUNT_IDS="1001,1003,1005" \
    COLD_ACCOUNT_IDS="1002,1004" \
    "${script}"
}

run_bootstrap_with_write_accounts() {
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
    STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_ID="920000001" \
    STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_ID="920000002" \
    STAGING_MIXED_WORKLOAD_WRITE_SOURCE_BALANCE_MINOR="50000000" \
    "${script}"
}

run_bootstrap_with_replay_token_file() {
  local token_file="${tmp_dir}/staging-replay-token.jwt"
  printf '%s' \
    'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdGFnaW5nLWZpeHR1cmUtdXNlciIsInVzZXJfaWQiOjU1LCJzZXNzaW9uX2lkIjoxMjM0NSwiZXhwIjoxODkzNDU2MDAwfQ.sig' \
    >"${token_file}"
  chmod 600 "${token_file}"

  env \
    PATH="${tmp_dir}:${PATH}" \
    PSQL_STUB_LOG="${psql_log}" \
    PSQL_STDIN_LOG="${psql_stdin_log}" \
    STAGING_OCI_A1_DATABASE_URL="postgresql://fixture-db/aquila" \
    STAGING_REPLAY_USER_ID="55" \
    STAGING_REPLAY_LOGIN_ID="staging-fixture-user" \
    STAGING_REPLAY_USER_PASSWORD_HASH="fixturePasswordHashWithLetters" \
    STAGING_REPLAY_USER_DISPLAY_NAME="Staging Fixture User" \
    STAGING_REPLAY_TOKEN_FILE="${token_file}" \
    HOT_ACCOUNT_ID="1001" \
    COLD_ACCOUNT_ID="1002" \
    "${script}"
}

run_bootstrap_with_mismatched_replay_token_file() {
  local token_file="${tmp_dir}/staging-replay-token-mismatch.jwt"
  printf '%s' \
    'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdGFnaW5nLWZpeHR1cmUtdXNlciIsInVzZXJfaWQiOjU2LCJzZXNzaW9uX2lkIjoxMjM0NSwiZXhwIjoxODkzNDU2MDAwfQ.sig' \
    >"${token_file}"
  chmod 600 "${token_file}"

  env \
    PATH="${tmp_dir}:${PATH}" \
    PSQL_STUB_LOG="${psql_log}" \
    PSQL_STDIN_LOG="${psql_stdin_log}" \
    STAGING_OCI_A1_DATABASE_URL="postgresql://fixture-db/aquila" \
    STAGING_REPLAY_USER_ID="55" \
    STAGING_REPLAY_LOGIN_ID="staging-fixture-user" \
    STAGING_REPLAY_USER_PASSWORD_HASH="fixturePasswordHashWithLetters" \
    STAGING_REPLAY_USER_DISPLAY_NAME="Staging Fixture User" \
    STAGING_REPLAY_TOKEN_FILE="${token_file}" \
    HOT_ACCOUNT_ID="1001" \
    COLD_ACCOUNT_ID="1002" \
    "${script}"
}

assert_valid_secret_like_values_do_not_enter_arithmetic() {
  run_bootstrap >/dev/null
  grep -q -- "fixture_password_hash=fixturePasswordHashWithLetters" "${psql_log}"
}

assert_csv_account_ids_feed_fixture_sql() {
  : >"${psql_log}"
  : >"${psql_stdin_log}"

  run_bootstrap_with_csv_accounts >/dev/null

  grep -q -- "hot_account_ids=1001,1003,1005" "${psql_log}"
  grep -q -- "cold_account_ids=1002,1004" "${psql_log}"
  grep -q -- "regexp_split_to_table(:'hot_account_ids', ',')" "${psql_stdin_log}"
  grep -q -- "regexp_split_to_table(:'cold_account_ids', ',')" "${psql_stdin_log}"
  grep -q -- "INSERT INTO user_account_membership" "${psql_stdin_log}"
}

assert_write_accounts_are_funded_and_authorized() {
  : >"${psql_log}"
  : >"${psql_stdin_log}"

  run_bootstrap_with_write_accounts >/dev/null

  grep -q -- "write_source_account_id=920000001" "${psql_log}"
  grep -q -- "write_target_account_id=920000002" "${psql_log}"
  grep -q -- "write_source_balance_minor=50000000" "${psql_log}"
  grep -q -- "'write_source' AS account_group" "${psql_stdin_log}"
  grep -q -- "'write_target' AS account_group" "${psql_stdin_log}"
  grep -q -- "desired_available_balance_minor" "${psql_stdin_log}"
  grep -q -- "GREATEST(account_balance_snapshot.available_balance_minor, EXCLUDED.available_balance_minor)" "${psql_stdin_log}"
  grep -q -- "INSERT INTO user_account_membership" "${psql_stdin_log}"
}

assert_replay_token_session_is_bootstrapped_without_token_leak() {
  : >"${psql_log}"
  : >"${psql_stdin_log}"

  run_bootstrap_with_replay_token_file >/dev/null

  grep -q -- "replay_session_id=12345" "${psql_log}"
  grep -q -- "replay_session_expires_epoch=1893456000" "${psql_log}"
  grep -q -- "INSERT INTO auth_refresh_token_session" "${psql_stdin_log}"
  grep -q -- "OVERRIDING SYSTEM VALUE" "${psql_stdin_log}"
  grep -q -- "pg_get_serial_sequence('auth_refresh_token_session', 'id')" "${psql_stdin_log}"
  if grep -F "eyJhbGciOiJIUzI1NiJ9" "${psql_log}" "${psql_stdin_log}" >/dev/null; then
    echo "replay bearer token must not be printed or passed to psql" >&2
    exit 1
  fi
}

assert_replay_token_user_mismatch_fails_before_psql() {
  local log="${tmp_dir}/replay-token-mismatch.log"
  : >"${psql_log}"
  : >"${psql_stdin_log}"

  if run_bootstrap_with_mismatched_replay_token_file >"${log}" 2>&1; then
    echo "expected replay token user mismatch to fail" >&2
    exit 1
  fi

  grep -q -- "STAGING_REPLAY_TOKEN user_id claim must match STAGING_REPLAY_USER_ID" "${log}"
  if [ -s "${psql_log}" ]; then
    echo "expected replay token mismatch to fail before psql execution" >&2
    exit 1
  fi
}

assert_transient_psql_timeout_retries_fixture_sql() {
  local output="${tmp_dir}/retry.log"
  local count_file="${tmp_dir}/psql-count"
  : >"${psql_log}"
  : >"${psql_stdin_log}"

  env \
    PATH="${tmp_dir}:${PATH}" \
    PSQL_STUB_LOG="${psql_log}" \
    PSQL_STDIN_LOG="${psql_stdin_log}" \
    PSQL_CALL_COUNT_FILE="${count_file}" \
    PSQL_FAIL_FIRST="true" \
    STAGING_FIXTURE_PRINCIPAL_DB_ATTEMPTS="2" \
    STAGING_FIXTURE_PRINCIPAL_DB_RETRY_SLEEP_SECONDS="0" \
    STAGING_OCI_A1_DATABASE_URL="postgresql://fixture-db/aquila" \
    STAGING_REPLAY_USER_ID="55" \
    STAGING_REPLAY_LOGIN_ID="staging-fixture-user" \
    STAGING_REPLAY_USER_PASSWORD_HASH="fixturePasswordHashWithLetters" \
    STAGING_REPLAY_USER_DISPLAY_NAME="Staging Fixture User" \
    HOT_ACCOUNT_ID="1001" \
    COLD_ACCOUNT_ID="1002" \
    "${script}" >"${output}" 2>&1

  grep -q -- "staging fixture principal psql attempt 1/2 failed; retrying in 0s" "${output}"
  grep -qx -- "2" "${count_file}"
  grep -q -- "INSERT INTO user_account_membership" "${psql_stdin_log}"
  grep -q -- "ensured user_id=55 hot_account_ids=1001 cold_account_ids=1002" "${output}"
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
assert_csv_account_ids_feed_fixture_sql
assert_write_accounts_are_funded_and_authorized
assert_replay_token_session_is_bootstrapped_without_token_leak
assert_replay_token_user_mismatch_fails_before_psql
assert_transient_psql_timeout_retries_fixture_sql
assert_too_long_password_hash_fails_before_psql

echo "[staging-fixture-principal-contract] ok"
