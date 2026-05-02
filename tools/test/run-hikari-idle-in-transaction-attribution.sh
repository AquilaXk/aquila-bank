#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-hikari-idle-in-transaction-attribution.sh [--print-plan|--print-sampler-sql]

Environment:
  HIKARI_IDLE_ATTRIBUTION_NAME                       default hikari-idle-attribution-<timestamp>
  HIKARI_IDLE_ATTRIBUTION_PG_ACTIVITY_TSV            required pg_stat_activity sampler TSV
  HIKARI_IDLE_ATTRIBUTION_HIKARI_LOG                 required Hikari warning log
  HIKARI_IDLE_ATTRIBUTION_CONFIG_TSV                 required key/value timeout config TSV
  HIKARI_IDLE_ATTRIBUTION_OUTPUT_DIR                 default build/reports/k6/<name>
  HIKARI_IDLE_ATTRIBUTION_CORRELATION_WINDOW_SECONDS default 5
USAGE
}

print_sampler_sql() {
  cat <<'SQL'
SELECT
  to_char(clock_timestamp() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS sample_time_utc,
  pid,
  usename,
  application_name,
  state,
  wait_event_type,
  wait_event,
  EXTRACT(EPOCH FROM (clock_timestamp() - xact_start))::bigint AS xact_age_seconds,
  left(regexp_replace(query, '\s+', ' ', 'g'), 240) AS query
FROM pg_stat_activity
WHERE datname = current_database()
  AND state IN ('idle in transaction', 'active')
ORDER BY sample_time_utc, pid;
SQL
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    --print-sampler-sql)
      mode="print-sampler-sql"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

if [[ "${mode}" == "print-sampler-sql" ]]; then
  print_sampler_sql
  exit 0
fi

name="${HIKARI_IDLE_ATTRIBUTION_NAME:-hikari-idle-attribution-$(date +%Y-%m-%d-%H%M%S)}"
pg_activity_tsv="${HIKARI_IDLE_ATTRIBUTION_PG_ACTIVITY_TSV:-}"
hikari_log="${HIKARI_IDLE_ATTRIBUTION_HIKARI_LOG:-}"
config_tsv="${HIKARI_IDLE_ATTRIBUTION_CONFIG_TSV:-}"
output_dir="${HIKARI_IDLE_ATTRIBUTION_OUTPUT_DIR:-build/reports/k6/${name}}"
correlation_window_seconds="${HIKARI_IDLE_ATTRIBUTION_CORRELATION_WINDOW_SECONDS:-5}"
summary_tsv="${output_dir}/${name}-hikari-idle-attribution.tsv"
config_summary_tsv="${output_dir}/${name}-hikari-idle-config.tsv"
report_md="${output_dir}/${name}-hikari-idle-attribution.md"

require_non_negative_integer() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${key} must be a non-negative integer: ${value}" >&2
    exit 1
  fi
}

require_file() {
  local key="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${key} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[hikari-idle-attribution] name=${name}"
  echo "[hikari-idle-attribution] pg_activity_tsv=${pg_activity_tsv:-missing}"
  echo "[hikari-idle-attribution] hikari_log=${hikari_log:-missing}"
  echo "[hikari-idle-attribution] config_tsv=${config_tsv:-missing}"
  echo "[hikari-idle-attribution] output_dir=${output_dir}"
  echo "[hikari-idle-attribution] sampler=pg_stat_activity"
  echo "[hikari-idle-attribution] correlation_window_seconds=${correlation_window_seconds}"
  echo "[hikari-idle-attribution] summary_tsv=${summary_tsv}"
  echo "[hikari-idle-attribution] config_summary_tsv=${config_summary_tsv}"
  echo "[hikari-idle-attribution] report_md=${report_md}"
}

require_non_negative_integer "HIKARI_IDLE_ATTRIBUTION_CORRELATION_WINDOW_SECONDS" "${correlation_window_seconds}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "HIKARI_IDLE_ATTRIBUTION_PG_ACTIVITY_TSV" "${pg_activity_tsv}"
  require_file "HIKARI_IDLE_ATTRIBUTION_HIKARI_LOG" "${hikari_log}"
  require_file "HIKARI_IDLE_ATTRIBUTION_CONFIG_TSV" "${config_tsv}"
  exit 0
fi

require_file "HIKARI_IDLE_ATTRIBUTION_PG_ACTIVITY_TSV" "${pg_activity_tsv}"
require_file "HIKARI_IDLE_ATTRIBUTION_HIKARI_LOG" "${hikari_log}"
require_file "HIKARI_IDLE_ATTRIBUTION_CONFIG_TSV" "${config_tsv}"
mkdir -p "${output_dir}"

awk -F '\t' '
NR == 1 { next }
{
  value[$1] = $2 + 0
}
END {
  status = "pass"
  reason = "ok"
  postgres_idle = value["postgres_idle_in_transaction_timeout_ms"]
  oci_nat = value["oci_nat_idle_timeout_ms"]
  max_lifetime = value["hikari_max_lifetime_ms"]
  keepalive = value["hikari_keepalive_time_ms"]
  worker_interval = value["scheduled_worker_max_interval_ms"]
  if (postgres_idle <= 0 || oci_nat <= 0 || max_lifetime <= 0 || keepalive <= 0 || worker_interval <= 0) {
    status = "fail"; reason = "missing-required-timeout"
  } else if (max_lifetime >= oci_nat) {
    status = "fail"; reason = "hikari-maxLifetime>=oci-nat-idle"
  } else if (keepalive >= max_lifetime) {
    status = "fail"; reason = "hikari-keepalive>=maxLifetime"
  } else if (worker_interval > postgres_idle) {
    status = "fail"; reason = "scheduled-worker-boundary>postgres-idle-timeout"
  }
  print "config_status\treason\tpostgres_idle_in_transaction_timeout_ms\toci_nat_idle_timeout_ms\thikari_max_lifetime_ms\thikari_keepalive_time_ms\tscheduled_worker_max_interval_ms"
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\n", status, reason, postgres_idle, oci_nat, max_lifetime, keepalive, worker_interval
}
' "${config_tsv}" >"${config_summary_tsv}"

config_status="$(awk -F '\t' 'NR == 2 { print $1 }' "${config_summary_tsv}")"
config_reason="$(awk -F '\t' 'NR == 2 { print $2 }' "${config_summary_tsv}")"

warning_tsv="${output_dir}/${name}-hikari-warnings.tsv"
awk '
/Failed to validate connection|This connection has been closed/ {
  print $1 "\t" $0
}
' "${hikari_log}" >"${warning_tsv}"

awk -F '\t' -v warnings="${warning_tsv}" '
function value(name, fallback) {
  if (!(name in col) || col[name] == "") return fallback
  return $(col[name])
}
BEGIN {
  while ((getline line < warnings) > 0) {
    split(line, parts, "\t")
    warning_seen[parts[1]] = 1
    warning_order[++warning_count] = parts[1]
  }
  close(warnings)
}
NR == 1 {
  for (i = 1; i <= NF; i++) {
    col[$i] = i
  }
  next
}
{
  ts = value("sample_time_utc", "")
  if (warning_seen[ts] && !(ts in matched)) {
    matched[ts] = 1
    pid[ts] = value("pid", "n/a")
    state[ts] = value("state", "n/a")
    wait_type[ts] = value("wait_event_type", "n/a")
    wait_event[ts] = value("wait_event", "n/a")
    age[ts] = value("xact_age_seconds", "0")
    query[ts] = value("query", "n/a")
  }
}
END {
  print "warning_time_utc\tstatus\tpid\tstate\twait_event_type\twait_event\txact_age_seconds\tquery\tcause"
  for (i = 1; i <= warning_count; i++) {
    ts = warning_order[i]
    if (matched[ts]) {
      cause = (state[ts] == "idle in transaction") ? "idle-in-transaction-candidate" : "postgres-timeout-candidate"
      printf "%s\tpass\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", ts, pid[ts], state[ts], wait_type[ts], wait_event[ts], age[ts], query[ts], cause
    } else {
      printf "%s\tfail\tn/a\tn/a\tn/a\tn/a\t0\tn/a\tunattributed-warning\n", ts
    }
  }
}
' "${pg_activity_tsv}" >"${summary_tsv}"

unattributed_count="$(awk -F '\t' 'NR > 1 && $2 == "fail" { count++ } END { print count + 0 }' "${summary_tsv}")"
warning_count="$(awk -F '\t' 'END { print NR + 0 }' "${warning_tsv}")"
gate_status="pass"
if [[ "${config_status}" != "pass" || "${unattributed_count}" != "0" || "${warning_count}" == "0" ]]; then
  gate_status="fail"
fi

correlation_table="$(awk -F '\t' '
  BEGIN {
    print "| Warning time | Status | PID | State | Wait type | Wait event | Xact age s | Query | Cause |"
    print "| --- | --- | ---: | --- | --- | --- | ---: | --- | --- |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | `%s` | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Hikari Idle-in-Transaction Attribution

## Summary

- gate_status=${gate_status}
- config_status=${config_status}
- config_reason=${config_reason}
- warning_count=${warning_count}
- unattributed_warning_count=${unattributed_count}
- sampler: pg_stat_activity
- correlation window seconds: ${correlation_window_seconds}
- OCI A1 lifetime alignment: maxLifetime < NAT idle, keepalive < maxLifetime, scheduled worker <= PostgreSQL idle timeout

## Correlation

${correlation_table}

## Contract Notes

- Hikari validation warning은 같은 timestamp의 pg_stat_activity sample과 먼저 연결한다.
- query text는 sampler에서 240자로 잘라 artifact 크기와 민감정보 노출 위험을 낮춘다.
- correlation이 없으면 pool warning의 운영 원인을 닫을 수 없으므로 gate에서 실패한다.

## Artifacts

- summary TSV: ${summary_tsv}
- config TSV: ${config_summary_tsv}
- pg activity TSV: ${pg_activity_tsv}
- Hikari log: ${hikari_log}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "Hikari idle-in-transaction attribution failed: ${summary_tsv}" >&2
  exit 1
fi
