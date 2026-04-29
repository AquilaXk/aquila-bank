#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATION_DIR="${FLYWAY_MIGRATION_DIR:-back/src/main/resources/db/migration}"
ALLOW_MARKER="${FLYWAY_BREAKING_ALLOW_MARKER:-flyway:allow-breaking-change}"

usage() {
  cat <<USAGE
Usage: tools/test/check-flyway-backward-compatible-migrations.sh [--self-test] [--all] [--base-ref REF] [--print-plan] [file ...]

Checks changed Flyway SQL migrations for blue/green unsafe patterns.
Use '${ALLOW_MARKER} <reason>' in a migration comment for deliberate exceptions.
USAGE
}

resolve_base_ref() {
  if [[ -n "${FLYWAY_MIGRATION_BASE_REF:-}" ]]; then
    printf '%s\n' "${FLYWAY_MIGRATION_BASE_REF}"
    return
  fi

  if [[ -n "${GITHUB_BASE_REF:-}" ]]; then
    printf 'origin/%s\n' "${GITHUB_BASE_REF}"
    return
  fi

  printf 'origin/main\n'
}

collect_changed_files() {
  local base_ref="$1"
  if ! git -C "${ROOT_DIR}" rev-parse --verify "${base_ref}^{commit}" >/dev/null 2>&1; then
    if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
      echo "[flyway-compat] base ref is not available: ${base_ref}" >&2
      exit 1
    fi
    return 0
  fi

  git -C "${ROOT_DIR}" diff --name-only --diff-filter=ACMR "${base_ref}...HEAD" -- "${MIGRATION_DIR}" |
    while IFS= read -r path; do
      if [[ "${path}" == *.sql ]]; then
        printf '%s/%s\n' "${ROOT_DIR}" "${path}"
      fi
    done
}

scan_files() {
  python3 - "${ALLOW_MARKER}" "$@" <<'PY'
import pathlib
import re
import sys

allow_marker = sys.argv[1]
files = [pathlib.Path(item) for item in sys.argv[2:]]

rules = [
    ("DROP TABLE", re.compile(r"\bDROP\s+TABLE\b", re.I | re.S)),
    ("DROP COLUMN", re.compile(r"\bALTER\s+TABLE\b[^;]*\bDROP\s+COLUMN\b", re.I | re.S)),
    ("DROP CONSTRAINT", re.compile(r"\bALTER\s+TABLE\b[^;]*\bDROP\s+CONSTRAINT\b", re.I | re.S)),
    ("RENAME TABLE OR COLUMN", re.compile(r"\bALTER\s+TABLE\b[^;]*\bRENAME\b", re.I | re.S)),
    ("ALTER COLUMN TYPE", re.compile(r"\bALTER\s+TABLE\b[^;]*\bALTER\s+COLUMN\b[^;]*\bTYPE\b", re.I | re.S)),
    ("SET NOT NULL", re.compile(r"\bALTER\s+TABLE\b[^;]*\bALTER\s+COLUMN\b[^;]*\bSET\s+NOT\s+NULL\b", re.I | re.S)),
    ("DROP INDEX", re.compile(r"\bDROP\s+INDEX\b", re.I | re.S)),
    ("DROP TYPE", re.compile(r"\bDROP\s+TYPE\b", re.I | re.S)),
    ("DROP SEQUENCE", re.compile(r"\bDROP\s+SEQUENCE\b", re.I | re.S)),
    ("TRUNCATE", re.compile(r"\bTRUNCATE\b", re.I | re.S)),
]

def strip_comments(sql: str) -> str:
    sql = re.sub(r"/\*.*?\*/", " ", sql, flags=re.S)
    sql = re.sub(r"--.*?$", " ", sql, flags=re.M)
    return sql

def compact(sql: str) -> str:
    return re.sub(r"\s+", " ", sql).strip()

violations = []

for path in files:
    raw = path.read_text(encoding="utf-8")
    if allow_marker in raw:
        if re.search(re.escape(allow_marker) + r"\s+\S+", raw):
            print(f"[flyway-compat] allow marker: {path}")
            continue
        violations.append((path, "ALLOW MARKER REASON", f"{allow_marker} requires a reason"))
        continue

    sql = compact(strip_comments(raw))
    if not sql:
        continue

    for name, pattern in rules:
        if pattern.search(sql):
            violations.append((path, name, pattern.pattern))

    for statement in [compact(item) for item in sql.split(";") if compact(item)]:
        upper_statement = statement.upper()
        if (
            "ALTER TABLE" in upper_statement
            and "ADD COLUMN" in upper_statement
            and "NOT NULL" in upper_statement
            and "DEFAULT" not in upper_statement
        ):
            violations.append((path, "ADD NOT NULL WITHOUT DEFAULT", statement[:180]))

if violations:
    print("[flyway-compat] backward-compatible migration gate failed", file=sys.stderr)
    print(
        f"[flyway-compat] deliberate exceptions require: -- {allow_marker} <reason>",
        file=sys.stderr,
    )
    for path, name, detail in violations:
        print(f"[flyway-compat] {path}: {name}: {detail}", file=sys.stderr)
    sys.exit(1)

print(f"[flyway-compat] checked {len(files)} migration file(s)")
PY
}

self_test() {
  local tmp_dir safe_sql unsafe_sql allowed_sql not_null_sql
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "${tmp_dir}"' RETURN

  safe_sql="${tmp_dir}/V998__safe_expand.sql"
  unsafe_sql="${tmp_dir}/V999__drop_column.sql"
  allowed_sql="${tmp_dir}/V1000__allowed_breaking_change.sql"
  not_null_sql="${tmp_dir}/V1001__not_null_without_default.sql"

  printf '%s\n' \
    "ALTER TABLE accounts ADD COLUMN memo VARCHAR(100);" \
    "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_created_at ON accounts (created_at);" \
    >"${safe_sql}"
  printf '%s\n' "ALTER TABLE accounts DROP COLUMN legacy_code;" >"${unsafe_sql}"
  printf '%s\n' \
    "-- ${ALLOW_MARKER} legacy_code is unused after two-phase deploy" \
    "ALTER TABLE accounts DROP COLUMN legacy_code;" \
    >"${allowed_sql}"
  printf '%s\n' "ALTER TABLE accounts ADD COLUMN status VARCHAR(20) NOT NULL;" >"${not_null_sql}"

  scan_files "${safe_sql}" >/dev/null
  if scan_files "${unsafe_sql}" >/dev/null 2>&1; then
    echo "[flyway-compat:self-test] unsafe DROP COLUMN was accepted" >&2
    return 1
  fi
  scan_files "${allowed_sql}" >/dev/null
  if scan_files "${not_null_sql}" >/dev/null 2>&1; then
    echo "[flyway-compat:self-test] NOT NULL without DEFAULT was accepted" >&2
    return 1
  fi

  echo "[flyway-compat:self-test] ok"
}

scan_all=false
print_plan=false
base_ref="$(resolve_base_ref)"
files=()

while (($# > 0)); do
  case "$1" in
    --self-test)
      self_test
      exit 0
      ;;
    --all)
      scan_all=true
      shift
      ;;
    --base-ref)
      if (($# < 2)); then
        echo "[flyway-compat] --base-ref requires a value" >&2
        exit 1
      fi
      base_ref="$2"
      shift 2
      ;;
    --print-plan)
      print_plan=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    -*)
      echo "[flyway-compat] unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
    *)
      files+=("$1")
      shift
      ;;
  esac
done

if [[ "${scan_all}" == "true" ]]; then
  while IFS= read -r path; do
    files+=("${path}")
  done < <(find "${ROOT_DIR}/${MIGRATION_DIR}" -maxdepth 1 -type f -name 'V*__*.sql' | sort)
elif ((${#files[@]} == 0)); then
  while IFS= read -r path; do
    files+=("${path}")
  done < <(collect_changed_files "${base_ref}")
fi

if [[ "${print_plan}" == "true" ]]; then
  echo "[flyway-compat] migration_dir=${MIGRATION_DIR}"
  echo "[flyway-compat] base_ref=${base_ref}"
  echo "[flyway-compat] allow_marker=${ALLOW_MARKER}"
  printf '[flyway-compat] files=%s\n' "${#files[@]}"
fi

if ((${#files[@]} == 0)); then
  echo "[flyway-compat] no changed Flyway migrations"
  exit 0
fi

scan_files "${files[@]}"
