#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/transaction-read-model-planner-stats-freshness-guard.sh"

echo "[transaction-planner-stats-guard] syntax: ${script}"
bash -n "${script}"

echo "[transaction-planner-stats-guard] dry-run: SQL exposes freshness signals and ANALYZE guidance"
sql="$("${script}" --print-sql)"
grep -F "n_mod_since_analyze" <<<"${sql}" >/dev/null
grep -F "last_autoanalyze" <<<"${sql}" >/dev/null
grep -F "stale_analyze_age" <<<"${sql}" >/dev/null
grep -F "stale_modified_ratio" <<<"${sql}" >/dev/null
grep -F "format('ANALYZE VERBOSE public.%I;', table_name)" <<<"${sql}" >/dev/null

echo "[transaction-planner-stats-guard] guard: invalid threshold fails before querying"
if STATS_MAX_AGE_HOURS=0 "${script}" --print-sql >/dev/null 2>&1; then
  echo "invalid STATS_MAX_AGE_HOURS unexpectedly succeeded" >&2
  exit 1
fi
if STATS_MAX_MODIFIED_RATIO=ratio "${script}" --print-sql >/dev/null 2>&1; then
  echo "invalid STATS_MAX_MODIFIED_RATIO unexpectedly succeeded" >&2
  exit 1
fi

echo "[transaction-planner-stats-guard] guard: psql variables are expanded from stdin SQL"
temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

bin_dir="${temp_dir}/bin"
mkdir -p "${bin_dir}"

cat >"${bin_dir}/psql" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

for arg in "$@"; do
  case "$arg" in
    --command|-c)
      echo "psql SQL must be passed through stdin for psql variable substitution" >&2
      exit 42
      ;;
  esac
done

sql="$(cat)"
grep -F ":'stats_max_age_hours'" <<<"${sql}" >/dev/null
grep -F ":'stats_max_modified_ratio'" <<<"${sql}" >/dev/null

cat <<'CSV'
table_name,status,row_estimate,live_tuple_count,modified_tuple_count,modified_ratio,last_analyze_at,analyze_age_hours,analyze_count,autoanalyze_count,analyze_command
transaction_read_model,ok,50000000,50000000,0,0,2026-04-29 00:00:00+00,1,1,1,ANALYZE VERBOSE public.transaction_read_model;
transaction_read_model_archive,ok,50000000,50000000,0,0,2026-04-29 00:00:00+00,1,1,1,ANALYZE VERBOSE public.transaction_read_model_archive;
CSV
EOF
chmod +x "${bin_dir}/psql"

output="$(
  PATH="${bin_dir}:${PATH}" \
  DATABASE_URL="postgresql://user:pass@localhost:5432/aquila" \
  "${script}"
)"

grep -F "transaction_read_model,ok" <<<"${output}" >/dev/null
grep -F "transaction_read_model_archive,ok" <<<"${output}" >/dev/null
