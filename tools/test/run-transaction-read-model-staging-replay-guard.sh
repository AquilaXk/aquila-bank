#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/transaction-read-model-staging-replay.sh"

echo "[transaction-staging-replay-guard] syntax: ${script}"
bash -n "${script}"

echo "[transaction-staging-replay-guard] distribution estimate uses leaf partitions"
grep -F "pg_partition_tree(('public.' || target.table_name)::regclass)" "${script}" >/dev/null
grep -F "tree.isleaf" "${script}" >/dev/null
grep -F "SUM(GREATEST(c.reltuples, 0))" "${script}" >/dev/null
if grep -F "awk -v index=" "${script}" >/dev/null; then
  echo "p95 calculation must not use gawk builtin name as variable" >&2
  exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

bin_dir="${temp_dir}/bin"
mkdir -p "${bin_dir}"

for command_name in curl jq psql awk sort; do
  cat >"${bin_dir}/${command_name}" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
  chmod +x "${bin_dir}/${command_name}"
done

guard_script="${temp_dir}/planner-guard.sh"
cat >"${guard_script}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "guard-called" >"${GUARD_MARKER_PATH}"
exit 19
EOF
chmod +x "${guard_script}"

psql_marker="${temp_dir}/psql-called"
cat >"${bin_dir}/psql" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "psql-called" >"${PSQL_MARKER_PATH}"
exit 0
EOF
chmod +x "${bin_dir}/psql"

guard_report_dir="${temp_dir}/guard-report"
set +e
output="$(
  PATH="${bin_dir}:${PATH}" \
  REPORT_DIR="${guard_report_dir}" \
  GUARD_MARKER_PATH="${temp_dir}/guard-called" \
  PSQL_MARKER_PATH="${psql_marker}" \
  PLANNER_STATS_GUARD_SCRIPT="${guard_script}" \
  PLANNER_STATS_AUTO_ANALYZE=false \
  STAGING_BASE_URL="https://staging.example.com" \
  STAGING_REPLAY_TOKEN="token" \
  STAGING_RDS_DATABASE_URL="postgres://user:pass@localhost:5432/db" \
  HOT_ACCOUNT_ID="101" \
  HOT_FROM="2026-04-01T00:00:00Z" \
  HOT_TO="2026-04-15T00:00:00Z" \
  COLD_ACCOUNT_ID="202" \
  COLD_FROM="2026-03-01T00:00:00Z" \
  COLD_TO="2026-03-31T00:00:00Z" \
  "${script}" 2>&1
)"
status=$?
set -e

if [ "${status}" -eq 0 ]; then
  echo "replay guard test unexpectedly succeeded" >&2
  exit 1
fi

grep -F "Planner stats freshness guard failed" <<<"${output}" >/dev/null
grep -F "planner_stats_guard_failed" "${guard_report_dir}/summary.md" >/dev/null
grep -F "Run ANALYZE on reported tables before replay." "${guard_report_dir}/summary.md" >/dev/null
test -f "${guard_report_dir}/summary.json"
[ -f "${temp_dir}/guard-called" ] || {
  echo "planner stats guard was not called" >&2
  exit 1
}

if [ -f "${psql_marker}" ]; then
  echo "psql must not run before planner stats guard passes" >&2
  exit 1
fi

echo "[transaction-staging-replay-guard] stale stats auto analyze retries guard"
guard_retry_script="${temp_dir}/planner-guard-retry.sh"
cat >"${guard_retry_script}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
count_file="${GUARD_RETRY_COUNT_PATH}"
count=0
if [ -f "${count_file}" ]; then
  count="$(cat "${count_file}")"
fi
count=$((count + 1))
echo "${count}" >"${count_file}"
if [ "${count}" -eq 1 ]; then
  exit 19
fi
exit 0
EOF
chmod +x "${guard_retry_script}"

analyze_script="${temp_dir}/analyze.sh"
cat >"${analyze_script}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [ "${DATABASE_URL:-}" != "postgres://user:pass@localhost:5432/db" ]; then
  echo "analyze DATABASE_URL mismatch: ${DATABASE_URL:-}" >&2
  exit 1
fi
if [ "$*" != "--action analyze --target both" ]; then
  echo "unexpected analyze args: $*" >&2
  exit 1
fi
echo "analyze-called" >"${ANALYZE_MARKER_PATH}"
EOF
chmod +x "${analyze_script}"

cat >"${bin_dir}/psql" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '0\n'
EOF
chmod +x "${bin_dir}/psql"

auto_report_dir="${temp_dir}/auto-report"
set +e
auto_output="$(
  PATH="${bin_dir}:${PATH}" \
  REPORT_DIR="${auto_report_dir}" \
  GUARD_RETRY_COUNT_PATH="${temp_dir}/guard-retry-count" \
  ANALYZE_MARKER_PATH="${temp_dir}/analyze-called" \
  PLANNER_STATS_GUARD_SCRIPT="${guard_retry_script}" \
  PLANNER_STATS_ANALYZE_SCRIPT="${analyze_script}" \
  STAGING_BASE_URL="https://staging.example.com" \
  STAGING_REPLAY_TOKEN="token" \
  STAGING_RDS_DATABASE_URL="postgres://user:pass@localhost:5432/db" \
  EXPECTED_TOTAL_ROWS="100" \
  EXPECTED_TOTAL_ROWS_TOLERANCE="1" \
  HOT_ACCOUNT_ID="101" \
  HOT_FROM="2026-04-01T00:00:00Z" \
  HOT_TO="2026-04-15T00:00:00Z" \
  COLD_ACCOUNT_ID="202" \
  COLD_FROM="2026-03-01T00:00:00Z" \
  COLD_TO="2026-03-31T00:00:00Z" \
  "${script}" 2>&1
)"
auto_status=$?
set -e

if [ "${auto_status}" -eq 0 ]; then
  echo "auto analyze replay unexpectedly succeeded" >&2
  exit 1
fi

grep -F "Planner stats freshness guard failed; running ANALYZE before replay." <<<"${auto_output}" >/dev/null
grep -F "fixture_missing" <<<"${auto_output}" >/dev/null
grep -Fx "2" "${temp_dir}/guard-retry-count" >/dev/null
grep -F "analyze-called" "${temp_dir}/analyze-called" >/dev/null

guard_success_script="${temp_dir}/planner-guard-success.sh"
cat >"${guard_success_script}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
exit 0
EOF
chmod +x "${guard_success_script}"

echo "[transaction-staging-replay-guard] planner estimate tolerance allows replay"
tolerance_bin_dir="${temp_dir}/tolerance-bin"
mkdir -p "${tolerance_bin_dir}"

cat >"${tolerance_bin_dir}/psql" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
count_file="${TOLERANCE_PSQL_COUNT_PATH}"
count=0
if [ -f "${count_file}" ]; then
  count="$(cat "${count_file}")"
fi
count=$((count + 1))
echo "${count}" >"${count_file}"

case "${count}" in
  1) printf '99\n' ;;
  2) printf 't\n' ;;
  3) printf 't\n' ;;
  *)
    echo "unexpected psql call: ${count}" >&2
    exit 1
    ;;
esac
EOF
chmod +x "${tolerance_bin_dir}/psql"

cat >"${tolerance_bin_dir}/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
output_file=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --output)
      output_file="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

[ -n "${output_file}" ] || {
  echo "missing --output" >&2
  exit 1
}
printf '{"items":[{"id":"tx"}],"nextCursor":"cursor"}\n' >"${output_file}"
printf '200 0.001'
EOF
chmod +x "${tolerance_bin_dir}/curl"

tolerance_report_dir="${temp_dir}/tolerance-report"
PATH="${tolerance_bin_dir}:${PATH}" \
  REPORT_DIR="${tolerance_report_dir}" \
  TOLERANCE_PSQL_COUNT_PATH="${temp_dir}/tolerance-psql-count" \
  PLANNER_STATS_GUARD_SCRIPT="${guard_success_script}" \
  PLANNER_STATS_AUTO_ANALYZE=false \
  STAGING_BASE_URL="https://staging.example.com" \
  STAGING_REPLAY_TOKEN="token" \
  STAGING_RDS_DATABASE_URL="postgres://user:pass@localhost:5432/db" \
  EXPECTED_TOTAL_ROWS="100" \
  EXPECTED_TOTAL_ROWS_TOLERANCE="1" \
  ITERATIONS="1" \
  PAGE_LIMIT="1" \
  HOT_P95_THRESHOLD_MS="100" \
  COLD_P95_THRESHOLD_MS="100" \
  HOT_ACCOUNT_ID="101" \
  HOT_FROM="2026-04-01T00:00:00Z" \
  HOT_TO="2026-04-15T00:00:00Z" \
  COLD_ACCOUNT_ID="202" \
  COLD_FROM="2026-03-01T00:00:00Z" \
  COLD_TO="2026-03-31T00:00:00Z" \
  "${script}"

grep -Fx "3" "${temp_dir}/tolerance-psql-count" >/dev/null
grep -Fx "99" "${tolerance_report_dir}/estimated-total-rows.txt" >/dev/null
jq -e '.expectedTotalRows == 100
  and .expectedTotalRowsTolerance == 1
  and .minimumExpectedTotalRows == 99
  and .estimatedTotalRows == 99
  and .failed == false' "${tolerance_report_dir}/summary.json" >/dev/null
grep -F "minimum expected total rows: 99" "${tolerance_report_dir}/summary.md" >/dev/null

echo "[transaction-staging-replay-guard] empty fixture failure writes report"
cat >"${bin_dir}/psql" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '0\n'
EOF
chmod +x "${bin_dir}/psql"

empty_report_dir="${temp_dir}/empty-report"
set +e
empty_output="$(
  PATH="${bin_dir}:${PATH}" \
  REPORT_DIR="${empty_report_dir}" \
  PLANNER_STATS_GUARD_SCRIPT="${guard_success_script}" \
  STAGING_BASE_URL="https://staging.example.com" \
  STAGING_REPLAY_TOKEN="token" \
  STAGING_RDS_DATABASE_URL="postgres://user:pass@localhost:5432/db" \
  EXPECTED_TOTAL_ROWS="100" \
  EXPECTED_TOTAL_ROWS_TOLERANCE="1" \
  HOT_ACCOUNT_ID="101" \
  HOT_FROM="2026-04-01T00:00:00Z" \
  HOT_TO="2026-04-15T00:00:00Z" \
  COLD_ACCOUNT_ID="202" \
  COLD_FROM="2026-03-01T00:00:00Z" \
  COLD_TO="2026-03-31T00:00:00Z" \
  "${script}" 2>&1
)"
empty_status=$?
set -e

if [ "${empty_status}" -eq 0 ]; then
  echo "empty fixture replay unexpectedly succeeded" >&2
  exit 1
fi

grep -F "fixture_missing" <<<"${empty_output}" >/dev/null
grep -F "OCI A1 read model estimate 0 is below minimum 99" <<<"${empty_output}" >/dev/null
grep -F "fixture_missing" "${empty_report_dir}/summary.md" >/dev/null
grep -F "tools/test/run-transaction-100m-fresh-volume-restore-k6.sh --dry-run" "${empty_report_dir}/summary.md" >/dev/null
grep -Fx "0" "${empty_report_dir}/estimated-total-rows.txt" >/dev/null
