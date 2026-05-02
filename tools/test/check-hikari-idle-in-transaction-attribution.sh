#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-hikari-idle-in-transaction-attribution.sh"

echo "[hikari-idle-attribution] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

pg_tsv="${temp_dir}/pg-activity.tsv"
hikari_log="${temp_dir}/hikari.log"
config_tsv="${temp_dir}/config.tsv"
output_dir="${temp_dir}/output"

cat >"${pg_tsv}" <<'TSV'
sample_time_utc	pid	usename	application_name	state	wait_event_type	wait_event	xact_age_seconds	query
2026-05-02T03:00:05Z	123	aquila	aquila-bank	idle in transaction	Client	ClientRead	182	select pg_sleep(30)
2026-05-02T03:00:10Z	124	aquila	aquila-bank	active	IO	DataFileRead	1	select 1
TSV

cat >"${hikari_log}" <<'LOG'
2026-05-02T03:00:05Z WARN com.zaxxer.hikari.pool.PoolBase - aquila-bank-pool - Failed to validate connection org.postgresql.jdbc.PgConnection@1 (This connection has been closed.)
LOG

cat >"${config_tsv}" <<'TSV'
key	value
postgres_idle_in_transaction_timeout_ms	300000
oci_nat_idle_timeout_ms	350000
hikari_max_lifetime_ms	240000
hikari_keepalive_time_ms	60000
scheduled_worker_max_interval_ms	120000
TSV

echo "[hikari-idle-attribution] print plan"
plan="$(
  HIKARI_IDLE_ATTRIBUTION_NAME=hikari-check \
  HIKARI_IDLE_ATTRIBUTION_PG_ACTIVITY_TSV="${pg_tsv}" \
  HIKARI_IDLE_ATTRIBUTION_HIKARI_LOG="${hikari_log}" \
  HIKARI_IDLE_ATTRIBUTION_CONFIG_TSV="${config_tsv}" \
  HIKARI_IDLE_ATTRIBUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=hikari-check" <<<"${plan}" >/dev/null
grep -F "sampler=pg_stat_activity" <<<"${plan}" >/dev/null
grep -F "correlation_window_seconds=5" <<<"${plan}" >/dev/null

echo "[hikari-idle-attribution] sampler SQL"
sampler_sql="$("${runner}" --print-sampler-sql)"
grep -F "pg_stat_activity" <<<"${sampler_sql}" >/dev/null
grep -F "idle in transaction" <<<"${sampler_sql}" >/dev/null
grep -F "xact_age_seconds" <<<"${sampler_sql}" >/dev/null

echo "[hikari-idle-attribution] pass report"
output="$(
  HIKARI_IDLE_ATTRIBUTION_NAME=hikari-check \
  HIKARI_IDLE_ATTRIBUTION_PG_ACTIVITY_TSV="${pg_tsv}" \
  HIKARI_IDLE_ATTRIBUTION_HIKARI_LOG="${hikari_log}" \
  HIKARI_IDLE_ATTRIBUTION_CONFIG_TSV="${config_tsv}" \
  HIKARI_IDLE_ATTRIBUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/hikari-check-hikari-idle-attribution.tsv"
test "${report_md}" = "${output_dir}/hikari-check-hikari-idle-attribution.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "config_status=pass" "${report_md}" >/dev/null
grep -F "OCI A1 lifetime alignment" "${report_md}" >/dev/null
grep -F $'warning_time_utc\tstatus\tpid\tstate\twait_event_type\twait_event\txact_age_seconds\tquery\tcause' "${summary_tsv}" >/dev/null
grep -F $'2026-05-02T03:00:05Z\tpass\t123\tidle in transaction\tClient\tClientRead\t182\tselect pg_sleep(30)\tidle-in-transaction-candidate' "${summary_tsv}" >/dev/null

echo "[hikari-idle-attribution] bad config fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "hikari_max_lifetime_ms" { $2 = 400000 } { print }' \
  "${config_tsv}" >"${config_tsv}.bad"
if HIKARI_IDLE_ATTRIBUTION_NAME=hikari-bad \
  HIKARI_IDLE_ATTRIBUTION_PG_ACTIVITY_TSV="${pg_tsv}" \
  HIKARI_IDLE_ATTRIBUTION_HIKARI_LOG="${hikari_log}" \
  HIKARI_IDLE_ATTRIBUTION_CONFIG_TSV="${config_tsv}.bad" \
  HIKARI_IDLE_ATTRIBUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "hikari attribution unexpectedly passed bad lifetime alignment" >&2
  exit 1
fi

echo "[hikari-idle-attribution] unattributed warning fails"
cat >"${hikari_log}.missing" <<'LOG'
2026-05-02T03:05:00Z WARN com.zaxxer.hikari.pool.PoolBase - aquila-bank-pool - Failed to validate connection org.postgresql.jdbc.PgConnection@2 (This connection has been closed.)
LOG
if HIKARI_IDLE_ATTRIBUTION_NAME=hikari-missing \
  HIKARI_IDLE_ATTRIBUTION_PG_ACTIVITY_TSV="${pg_tsv}" \
  HIKARI_IDLE_ATTRIBUTION_HIKARI_LOG="${hikari_log}.missing" \
  HIKARI_IDLE_ATTRIBUTION_CONFIG_TSV="${config_tsv}" \
  HIKARI_IDLE_ATTRIBUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "hikari attribution unexpectedly passed unattributed warning" >&2
  exit 1
fi
