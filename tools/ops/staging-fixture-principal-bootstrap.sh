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
HOT_ACCOUNT_IDS="${HOT_ACCOUNT_IDS:-${STAGING_REPLAY_HOT_ACCOUNT_IDS:-${HOT_ACCOUNT_ID}}}"
COLD_ACCOUNT_IDS="${COLD_ACCOUNT_IDS:-${STAGING_REPLAY_COLD_ACCOUNT_IDS:-${COLD_ACCOUNT_ID}}}"
STAGING_REPLAY_HOT_ACCOUNT_NUMBER="${STAGING_REPLAY_HOT_ACCOUNT_NUMBER:-}"
STAGING_REPLAY_COLD_ACCOUNT_NUMBER="${STAGING_REPLAY_COLD_ACCOUNT_NUMBER:-}"
STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_ID="${STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_ID:-${MIXED_WORKLOAD_OCI_WRITE_SOURCE_ACCOUNT_ID:-${K6_WRITE_SOURCE_ACCOUNT_ID:-}}}"
STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_ID="${STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_ID:-${MIXED_WORKLOAD_OCI_WRITE_TARGET_ACCOUNT_ID:-${K6_WRITE_TARGET_ACCOUNT_ID:-}}}"
STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_NUMBER="${STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_NUMBER:-}"
STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_NUMBER="${STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_NUMBER:-}"
STAGING_MIXED_WORKLOAD_WRITE_SOURCE_BALANCE_MINOR="${STAGING_MIXED_WORKLOAD_WRITE_SOURCE_BALANCE_MINOR:-50000000}"
STAGING_MIXED_WORKLOAD_WRITE_TARGET_BALANCE_MINOR="${STAGING_MIXED_WORKLOAD_WRITE_TARGET_BALANCE_MINOR:-0}"
STAGING_FIXTURE_PRINCIPAL_DB_ATTEMPTS="${STAGING_FIXTURE_PRINCIPAL_DB_ATTEMPTS:-3}"
STAGING_FIXTURE_PRINCIPAL_DB_RETRY_SLEEP_SECONDS="${STAGING_FIXTURE_PRINCIPAL_DB_RETRY_SLEEP_SECONDS:-5}"
STAGING_REPLAY_TOKEN_FILE="${STAGING_REPLAY_TOKEN_FILE:-}"
STAGING_REPLAY_SESSION_ID=""
STAGING_REPLAY_SESSION_EXPIRES_EPOCH=""
STAGING_REPLAY_SESSION_TOKEN_HASH=""
STAGING_REPLAY_SESSION_DEVICE_BINDING_HASH=""

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

require_non_negative_integer() {
  local name="$1"
  local value="${!name:-}"
  [[ "$value" =~ ^[0-9]+$ ]] || fail "${name} must be a non-negative integer"
}

normalize_account_ids() {
  local name="$1"
  local raw="${!name:-}"
  local normalized=""
  local item
  local -a items
  raw="${raw//[[:space:]]/}"
  [ -n "${raw}" ] || fail "Missing required environment variable: ${name}"

  IFS="," read -r -a items <<<"${raw}"
  for item in "${items[@]}"; do
    [ -n "${item}" ] || fail "${name} must not contain empty account ids"
    [[ "${item}" =~ ^[1-9][0-9]*$ ]] || fail "${name} must contain only positive integer account ids"
    if [[ -n "${normalized}" ]]; then
      normalized+=","
    fi
    normalized+="${item}"
  done

  printf -v "${name}" '%s' "${normalized}"
}

require_max_length() {
  local name="$1"
  local value="${!name:-}"
  local max="$2"
  if ((${#value} > max)); then
    fail "${name} must be ${max} characters or less"
  fi
}

require_account_number_lengths() {
  local group_name="$1"
  local account_ids="$2"
  local first_account_number="$3"
  local fallback_prefix="$4"
  local account_id account_number
  local index=0
  local -a account_items

  IFS="," read -r -a account_items <<<"${account_ids}"
  for account_id in "${account_items[@]}"; do
    ((index += 1))
    if ((index == 1)); then
      account_number="${first_account_number}"
    else
      account_number="${fallback_prefix}${account_id}"
    fi
    if ((${#account_number} > 20)); then
      fail "${group_name} account number for account_id=${account_id} must be 20 characters or less"
    fi
  done
}

resolve_replay_token_session() {
  local token_file="${STAGING_REPLAY_TOKEN_FILE}"
  local claim_values

  [[ -n "${token_file}" ]] || return 0
  [[ -s "${token_file}" ]] || fail "STAGING_REPLAY_TOKEN_FILE must be a non-empty file"

  require_command python3
  if ! claim_values="$(python3 - "${STAGING_REPLAY_USER_ID}" "${token_file}" <<'PY'
import base64
import hashlib
import json
import pathlib
import sys
import time

expected_user_id = int(sys.argv[1])
token = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8").strip()
parts = token.split(".")
if len(parts) < 2:
    raise SystemExit("STAGING_REPLAY_TOKEN must be a JWT")

payload_segment = parts[1]
payload_segment += "=" * (-len(payload_segment) % 4)
try:
    payload = json.loads(base64.urlsafe_b64decode(payload_segment.encode("ascii")))
except Exception as exc:  # noqa: BLE001 - sanitized CLI error only
    raise SystemExit(f"STAGING_REPLAY_TOKEN payload decode failed: {exc}") from exc


def positive_int(name: str) -> int:
    value = payload.get(name)
    if isinstance(value, bool):
        raise SystemExit(f"STAGING_REPLAY_TOKEN {name} claim must be a positive integer")
    if isinstance(value, int):
        result = value
    elif isinstance(value, str) and value.isdigit():
        result = int(value)
    else:
        raise SystemExit(f"STAGING_REPLAY_TOKEN {name} claim must be a positive integer")
    if result <= 0:
        raise SystemExit(f"STAGING_REPLAY_TOKEN {name} claim must be a positive integer")
    return result


user_id = positive_int("user_id")
session_id = positive_int("session_id")
expires_epoch = positive_int("exp")
if user_id != expected_user_id:
    raise SystemExit("STAGING_REPLAY_TOKEN user_id claim must match STAGING_REPLAY_USER_ID")
if expires_epoch <= int(time.time()):
    raise SystemExit("STAGING_REPLAY_TOKEN exp claim is expired")

token_hash = hashlib.sha256(f"staging-replay-refresh:{user_id}:{session_id}".encode()).hexdigest()
device_binding_hash = hashlib.sha256(
    f"staging-replay-device:{user_id}:{session_id}".encode()
).hexdigest()
print(f"{user_id}\t{session_id}\t{expires_epoch}\t{token_hash}\t{device_binding_hash}")
PY
  )"; then
    fail "${claim_values}"
  fi

  IFS=$'\t' read -r _ STAGING_REPLAY_SESSION_ID STAGING_REPLAY_SESSION_EXPIRES_EPOCH STAGING_REPLAY_SESSION_TOKEN_HASH STAGING_REPLAY_SESSION_DEVICE_BINDING_HASH <<<"${claim_values}"
}

validate_optional_write_accounts() {
  local has_source=false
  local has_target=false
  [[ -n "${STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_ID}" ]] && has_source=true
  [[ -n "${STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_ID}" ]] && has_target=true
  if [[ "${has_source}" == "false" && "${has_target}" == "false" ]]; then
    return
  fi
  [[ "${has_source}" == "true" ]] || fail "STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_ID is required when write target is set"
  [[ "${has_target}" == "true" ]] || fail "STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_ID is required when write source is set"
  require_positive_integer STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_ID
  require_positive_integer STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_ID
  if [[ "${STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_ID}" == "${STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_ID}" ]]; then
    fail "STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_ID and STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_ID must differ"
  fi
  require_positive_integer STAGING_MIXED_WORKLOAD_WRITE_SOURCE_BALANCE_MINOR
  require_non_negative_integer STAGING_MIXED_WORKLOAD_WRITE_TARGET_BALANCE_MINOR
  STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_NUMBER="${STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_NUMBER:-STG-WR-SRC-${STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_ID}}"
  STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_NUMBER="${STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_NUMBER:-STG-WR-TGT-${STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_ID}}"
  require_max_length STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_NUMBER 20
  require_max_length STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_NUMBER 20
}

validate_inputs() {
  require_command psql
  require_env STAGING_DATABASE_URL
  require_env STAGING_REPLAY_LOGIN_ID
  require_env STAGING_REPLAY_USER_PASSWORD_HASH
  require_env STAGING_REPLAY_USER_DISPLAY_NAME
  require_env HOT_ACCOUNT_IDS
  require_env COLD_ACCOUNT_IDS
  normalize_account_ids HOT_ACCOUNT_IDS
  normalize_account_ids COLD_ACCOUNT_IDS
  HOT_ACCOUNT_ID="${HOT_ACCOUNT_IDS%%,*}"
  COLD_ACCOUNT_ID="${COLD_ACCOUNT_IDS%%,*}"
  STAGING_REPLAY_HOT_ACCOUNT_NUMBER="${STAGING_REPLAY_HOT_ACCOUNT_NUMBER:-STG-HOT-${HOT_ACCOUNT_ID}}"
  STAGING_REPLAY_COLD_ACCOUNT_NUMBER="${STAGING_REPLAY_COLD_ACCOUNT_NUMBER:-STG-COLD-${COLD_ACCOUNT_ID}}"
  require_positive_integer STAGING_REPLAY_USER_ID
  require_max_length STAGING_REPLAY_LOGIN_ID 80
  require_max_length STAGING_REPLAY_USER_PASSWORD_HASH 120
  require_max_length STAGING_REPLAY_USER_DISPLAY_NAME 80
  require_max_length STAGING_REPLAY_HOT_ACCOUNT_NUMBER 20
  require_max_length STAGING_REPLAY_COLD_ACCOUNT_NUMBER 20
  require_account_number_lengths "HOT_ACCOUNT_IDS" "${HOT_ACCOUNT_IDS}" "${STAGING_REPLAY_HOT_ACCOUNT_NUMBER}" "STG-HOT-"
  require_account_number_lengths "COLD_ACCOUNT_IDS" "${COLD_ACCOUNT_IDS}" "${STAGING_REPLAY_COLD_ACCOUNT_NUMBER}" "STG-COLD-"
  validate_optional_write_accounts
  require_positive_integer STAGING_FIXTURE_PRINCIPAL_DB_ATTEMPTS
  require_non_negative_integer STAGING_FIXTURE_PRINCIPAL_DB_RETRY_SLEEP_SECONDS
  resolve_replay_token_session
}

ensure_fixture_principal() {
  # replay JWT는 user_id claim을 고정하므로, smoke/replay 전에 권한 row도 같은 id로 고정합니다.
  local attempt=1
  while true; do
    if psql "${STAGING_DATABASE_URL}" \
    -v ON_ERROR_STOP=1 \
    -v fixture_user_id="${STAGING_REPLAY_USER_ID}" \
    -v fixture_login_id="${STAGING_REPLAY_LOGIN_ID}" \
    -v fixture_password_hash="${STAGING_REPLAY_USER_PASSWORD_HASH}" \
    -v fixture_display_name="${STAGING_REPLAY_USER_DISPLAY_NAME}" \
    -v hot_account_id="${HOT_ACCOUNT_ID}" \
    -v cold_account_id="${COLD_ACCOUNT_ID}" \
    -v hot_account_ids="${HOT_ACCOUNT_IDS}" \
    -v cold_account_ids="${COLD_ACCOUNT_IDS}" \
    -v hot_account_number="${STAGING_REPLAY_HOT_ACCOUNT_NUMBER}" \
    -v cold_account_number="${STAGING_REPLAY_COLD_ACCOUNT_NUMBER}" \
    -v write_source_account_id="${STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_ID}" \
    -v write_target_account_id="${STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_ID}" \
    -v write_source_account_number="${STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_NUMBER}" \
    -v write_target_account_number="${STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_NUMBER}" \
    -v write_source_balance_minor="${STAGING_MIXED_WORKLOAD_WRITE_SOURCE_BALANCE_MINOR}" \
    -v write_target_balance_minor="${STAGING_MIXED_WORKLOAD_WRITE_TARGET_BALANCE_MINOR}" \
    -v replay_session_id="${STAGING_REPLAY_SESSION_ID}" \
    -v replay_session_expires_epoch="${STAGING_REPLAY_SESSION_EXPIRES_EPOCH}" \
    -v replay_session_token_hash="${STAGING_REPLAY_SESSION_TOKEN_HASH}" \
    -v replay_session_device_binding_hash="${STAGING_REPLAY_SESSION_DEVICE_BINDING_HASH}" <<'SQL'
      -- psql 변수(:name)는 -c 경로에서 치환되지 않아 stdin으로 전달한다.
      BEGIN;

      CREATE TEMP TABLE staging_fixture_accounts ON COMMIT DROP AS
      WITH raw_fixture_accounts AS (
          SELECT
              'hot' AS account_group,
              0 AS group_rank,
              hot.ordinal,
              hot.account_id_text::bigint AS account_id,
              CASE
                  WHEN hot.ordinal = 1 THEN :'hot_account_number'
                  ELSE concat('STG-HOT-', hot.account_id_text)
              END AS account_number,
              'Staging hot fixture account' AS display_name,
              0::bigint AS desired_available_balance_minor
          FROM regexp_split_to_table(:'hot_account_ids', ',') WITH ORDINALITY AS hot(account_id_text, ordinal)
          UNION ALL
          SELECT
              'cold' AS account_group,
              1 AS group_rank,
              cold.ordinal,
              cold.account_id_text::bigint AS account_id,
              CASE
                  WHEN cold.ordinal = 1 THEN :'cold_account_number'
                  ELSE concat('STG-COLD-', cold.account_id_text)
              END AS account_number,
              'Staging cold fixture account' AS display_name,
              0::bigint AS desired_available_balance_minor
          FROM regexp_split_to_table(:'cold_account_ids', ',') WITH ORDINALITY AS cold(account_id_text, ordinal)
          UNION ALL
          SELECT
              'write_source' AS account_group,
              2 AS group_rank,
              1 AS ordinal,
              NULLIF(:'write_source_account_id', '')::bigint AS account_id,
              :'write_source_account_number' AS account_number,
              'Staging mixed workload write source account' AS display_name,
              :'write_source_balance_minor'::bigint AS desired_available_balance_minor
          WHERE NULLIF(:'write_source_account_id', '') IS NOT NULL
          UNION ALL
          SELECT
              'write_target' AS account_group,
              3 AS group_rank,
              1 AS ordinal,
              NULLIF(:'write_target_account_id', '')::bigint AS account_id,
              :'write_target_account_number' AS account_number,
              'Staging mixed workload write target account' AS display_name,
              :'write_target_balance_minor'::bigint AS desired_available_balance_minor
          WHERE NULLIF(:'write_target_account_id', '') IS NOT NULL
      ),
      fixture_accounts AS (
          SELECT
              account_id,
              account_number,
              display_name,
              max(desired_available_balance_minor) OVER (PARTITION BY account_id) AS desired_available_balance_minor,
              group_rank,
              ordinal
          FROM raw_fixture_accounts
      )
      SELECT DISTINCT ON (account_id)
          account_id,
          account_number,
          display_name,
          desired_available_balance_minor
      FROM fixture_accounts
      ORDER BY account_id, group_rank, ordinal;

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
      SELECT
          account_id,
          account_number,
          display_name,
          'ACTIVE',
          'KRW',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      FROM staging_fixture_accounts
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
      SELECT
          account_id,
          0,
          desired_available_balance_minor,
          0,
          'KRW',
          CURRENT_TIMESTAMP
      FROM staging_fixture_accounts
      ON CONFLICT (account_id)
      DO UPDATE
      SET available_balance_minor = GREATEST(account_balance_snapshot.available_balance_minor, EXCLUDED.available_balance_minor),
          currency_code = EXCLUDED.currency_code,
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

      -- mixed workload write는 current session active gate를 통과해야 하므로 replay JWT의 session row를 맞춘다.
      SELECT CASE
          WHEN EXISTS (
              SELECT 1
              FROM auth_refresh_token_session
              WHERE id = NULLIF(:'replay_session_id', '')::bigint
                AND user_id <> :fixture_user_id
          )
          THEN CAST('replay session id belongs to another user' AS integer)
          ELSE 1
      END
      WHERE NULLIF(:'replay_session_id', '') IS NOT NULL;

      INSERT INTO auth_refresh_token_session (
          id,
          user_id,
          token_hash,
          device_binding_hash,
          device_name,
          ip_address,
          session_status,
          expires_at,
          created_at,
          updated_at
      )
      OVERRIDING SYSTEM VALUE
      SELECT
          NULLIF(:'replay_session_id', '')::bigint,
          :fixture_user_id,
          :'replay_session_token_hash',
          :'replay_session_device_binding_hash',
          'staging-mixed-workload',
          '127.0.0.1',
          'ACTIVE',
          to_timestamp(NULLIF(:'replay_session_expires_epoch', '')::double precision),
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      WHERE NULLIF(:'replay_session_id', '') IS NOT NULL
      ON CONFLICT (id)
      DO UPDATE
      SET token_hash = EXCLUDED.token_hash,
          device_binding_hash = EXCLUDED.device_binding_hash,
          device_name = EXCLUDED.device_name,
          ip_address = EXCLUDED.ip_address,
          session_status = 'ACTIVE',
          expires_at = GREATEST(auth_refresh_token_session.expires_at, EXCLUDED.expires_at),
          updated_at = CURRENT_TIMESTAMP
      WHERE auth_refresh_token_session.user_id = EXCLUDED.user_id;

      INSERT INTO user_account_membership (
          user_id,
          account_id,
          membership_role,
          membership_status,
          created_at,
          updated_at
      )
      SELECT
          :fixture_user_id,
          account_id,
          'OWNER',
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      FROM staging_fixture_accounts
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

      SELECT setval(
          pg_get_serial_sequence('auth_refresh_token_session', 'id'),
          GREATEST((SELECT COALESCE(MAX(id), 1) FROM auth_refresh_token_session), 1),
          true
      )
      WHERE NULLIF(:'replay_session_id', '') IS NOT NULL;

      COMMIT;
SQL
    then
      return 0
    fi

    if ((attempt >= STAGING_FIXTURE_PRINCIPAL_DB_ATTEMPTS)); then
      fail "staging fixture principal psql failed after ${attempt} attempts"
    fi

    echo "::warning::staging fixture principal psql attempt ${attempt}/${STAGING_FIXTURE_PRINCIPAL_DB_ATTEMPTS} failed; retrying in ${STAGING_FIXTURE_PRINCIPAL_DB_RETRY_SLEEP_SECONDS}s" >&2
    sleep "${STAGING_FIXTURE_PRINCIPAL_DB_RETRY_SLEEP_SECONDS}"
    ((attempt += 1))
  done
}

validate_inputs
ensure_fixture_principal

replay_session_status="absent"
if [[ -n "${STAGING_REPLAY_SESSION_ID}" ]]; then
  replay_session_status="present"
fi
echo "[staging-fixture-principal] ensured user_id=${STAGING_REPLAY_USER_ID} hot_account_ids=${HOT_ACCOUNT_IDS} cold_account_ids=${COLD_ACCOUNT_IDS} replay_session=${replay_session_status}"
