#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/transaction-read-model-staging-replay.sh"

echo "[transaction-staging-replay-guard] syntax: ${script}"
bash -n "${script}"

echo "[transaction-staging-replay-guard] distribution estimate uses leaf partitions"
grep -F "pg_partition_tree(('public.' || target.table_name)::regclass)" "${script}" >/dev/null
grep -F "tree.isleaf" "${script}" >/dev/null
grep -F "SUM(GREATEST(c.reltuples, 0))" "${script}" >/dev/null

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

set +e
output="$(
  PATH="${bin_dir}:${PATH}" \
  GUARD_MARKER_PATH="${temp_dir}/guard-called" \
  PSQL_MARKER_PATH="${psql_marker}" \
  PLANNER_STATS_GUARD_SCRIPT="${guard_script}" \
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
[ -f "${temp_dir}/guard-called" ] || {
  echo "planner stats guard was not called" >&2
  exit 1
}

if [ -f "${psql_marker}" ]; then
  echo "psql must not run before planner stats guard passes" >&2
  exit 1
fi
