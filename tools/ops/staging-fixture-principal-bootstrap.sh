#!/usr/bin/env bash
set -euo pipefail

STAGING_OCI_A1_DATABASE_URL="${STAGING_OCI_A1_DATABASE_URL:-}"
STAGING_RDS_DATABASE_URL="${STAGING_RDS_DATABASE_URL:-}"
STAGING_DATABASE_URL="${STAGING_OCI_A1_DATABASE_URL:-${STAGING_RDS_DATABASE_URL}}"
STAGING_REPLAY_USER_ID="${STAGING_REPLAY_USER_ID:-55}"
STAGING_REPLAY_LOGIN_ID="${STAGING_REPLAY_LOGIN_ID:-staging-fixture-user}"
STAGING_REPLAY_USER_PASSWORD_HASH="${STAGING_REPLAY_USER_PASSWORD_HASH:-staging-fixture-password-hash-not-for-login}"
STAGING_REPLAY_USER_DISPLAY_NAME="${STAGING_REPLAY_USER_DISPLAY_NAME:-Staging Fixture User}"
HOT_ACCOUNT_ID="${HOT_ACCOUNT_ID:-${STAGING_REPLAY_HOT_ACCOUNT_ID:-}}"
COLD_ACCOUNT_ID="${COLD_ACCOUNT_ID:-${STAGING_REPLAY_COLD_ACCOUNT_ID:-}}"
STAGING_REPLAY_HOT_ACCOUNT_NUMBER="${STAGING_REPLAY_HOT_ACCOUNT_NUMBER:-STG-HOT-${HOT_ACCOUNT_ID}}"
STAGING_REPLAY_COLD_ACCOUNT_NUMBER="${STAGING_REPLAY_COLD_ACCOUNT_NUMBER:-STG-COLD-${COLD_ACCOUNT_ID}}"

fail() {
  echo "::error::$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

require_env() {
  local name="$1"
  local value="${!name:-}"
  [ -n "$value" ] || fail "Missing required environment variable: ${name}"
}

require_positive_integer() {
  local name="$1"
  local value="${!name:-}"
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || fail "${name} must be a positive integer"
}

require_max_length() {
  local name="$1"
  local value="${!name:-}"
  local max="$2"
  if ((${#value} > max)); then
    fail "${name} must be ${max} characters or less"
  fi
}

validate_inputs() {
  require_command psql
  require_env STAGING_DATABASE_URL
  require_env STAGING_REPLAY_LOGIN_ID
  require_env STAGING_REPLAY_USER_PASSWORD_HASH
  require_env STAGING_REPLAY_USER_DISPLAY_NAME
  require_env HOT_ACCOUNT_ID
  require_env COLD_ACCOUNT_ID
  require_positive_integer STAGING_REPLAY_USER_ID
  require_positive_integer HOT_ACCOUNT_ID
  require_positive_integer COLD_ACCOUNT_ID
  require_max_length STAGING_REPLAY_LOGIN_ID 80
  require_max_length STAGING_REPLAY_USER_PASSWORD_HASH 120
  require_max_length STAGING_REPLAY_USER_DISPLAY_NAME 80
  require_max_length STAGING_REPLAY_HOT_ACCOUNT_NUMBER 20
  require_max_length STAGING_REPLAY_COLD_ACCOUNT_NUMBER 20
}

ensure_fixture_principal() {
  # replay JWT는 user_id claim을 고정하므로, smoke/replay 전에 권한 row도 같은 id로 고정합니다.
  psql "${STAGING_DATABASE_URL}" \
    -v ON_ERROR_STOP=1 \
    -v fixture_user_id="${STAGING_REPLAY_USER_ID}" \
    -v fixture_login_id="${STAGING_REPLAY_LOGIN_ID}" \
    -v fixture_password_hash="${STAGING_REPLAY_USER_PASSWORD_HASH}" \
    -v fixture_display_name="${STAGING_REPLAY_USER_DISPLAY_NAME}" \
    -v hot_account_id="${HOT_ACCOUNT_ID}" \
    -v cold_account_id="${COLD_ACCOUNT_ID}" \
    -v hot_account_number="${STAGING_REPLAY_HOT_ACCOUNT_NUMBER}" \
    -v cold_account_number="${STAGING_REPLAY_COLD_ACCOUNT_NUMBER}" \
    --command "
      BEGIN;

      INSERT INTO bank_account (
          id,
          account_number,
          display_name,
          account_status,
          currency_code,
          created_at,
          updated_at
      )
      OVERRIDING SYSTEM VALUE
      VALUES
          (:hot_account_id, :'hot_account_number', 'Staging hot fixture account', 'ACTIVE', 'KRW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
          (:cold_account_id, :'cold_account_number', 'Staging cold fixture account', 'ACTIVE', 'KRW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      ON CONFLICT (id)
      DO UPDATE
      SET account_status = 'ACTIVE',
          updated_at = CURRENT_TIMESTAMP;

      INSERT INTO account_balance_snapshot (
          account_id,
          last_applied_ledger_entry_id,
          available_balance_minor,
          pending_balance_minor,
          currency_code,
          updated_at
      )
      VALUES
          (:hot_account_id, 0, 0, 0, 'KRW', CURRENT_TIMESTAMP),
          (:cold_account_id, 0, 0, 0, 'KRW', CURRENT_TIMESTAMP)
      ON CONFLICT (account_id)
      DO UPDATE
      SET currency_code = EXCLUDED.currency_code,
          updated_at = CURRENT_TIMESTAMP;

      INSERT INTO bank_user (
          id,
          login_id,
          password_hash,
          display_name,
          user_status,
          created_at,
          updated_at
      )
      OVERRIDING SYSTEM VALUE
      VALUES (
          :fixture_user_id,
          :'fixture_login_id',
          :'fixture_password_hash',
          :'fixture_display_name',
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      )
      ON CONFLICT (id)
      DO UPDATE
      SET login_id = EXCLUDED.login_id,
          display_name = EXCLUDED.display_name,
          user_status = 'ACTIVE',
          updated_at = CURRENT_TIMESTAMP;

      INSERT INTO user_account_membership (
          user_id,
          account_id,
          membership_role,
          membership_status,
          created_at,
          updated_at
      )
      VALUES
          (:fixture_user_id, :hot_account_id, 'OWNER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
          (:fixture_user_id, :cold_account_id, 'OWNER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      ON CONFLICT (user_id, account_id)
      DO UPDATE
      SET membership_role = EXCLUDED.membership_role,
          membership_status = 'ACTIVE',
          updated_at = CURRENT_TIMESTAMP;

      SELECT setval(
          pg_get_serial_sequence('bank_user', 'id'),
          GREATEST((SELECT COALESCE(MAX(id), 1) FROM bank_user), 1),
          true
      );

      COMMIT;
    "
}

validate_inputs
ensure_fixture_principal

echo "[staging-fixture-principal] ensured user_id=${STAGING_REPLAY_USER_ID} hot_account_id=${HOT_ACCOUNT_ID} cold_account_id=${COLD_ACCOUNT_ID}"
