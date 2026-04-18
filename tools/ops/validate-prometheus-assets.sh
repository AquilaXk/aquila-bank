#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DASHBOARD_FILE="${ROOT_DIR}/ops/prometheus/grafana/aquila-bank-overview.json"
RULES_FILE="${ROOT_DIR}/ops/prometheus/rules/aquila-bank-alerts.yml"

if [[ ! -f "${DASHBOARD_FILE}" ]]; then
  echo "dashboard file is missing: ${DASHBOARD_FILE}" >&2
  exit 1
fi

if [[ ! -f "${RULES_FILE}" ]]; then
  echo "rules file is missing: ${RULES_FILE}" >&2
  exit 1
fi

jq -e '.uid == "aquila-bank-overview" and (.panels | type == "array" and length >= 8)' \
  "${DASHBOARD_FILE}" >/dev/null

ruby -e '
require "yaml"

data = YAML.safe_load(File.read(ARGV[0]), permitted_classes: [], aliases: false)
abort("groups missing") unless data.is_a?(Hash) && data["groups"].is_a?(Array) && !data["groups"].empty?

data["groups"].each do |group|
  abort("group name missing") if group["name"].to_s.empty?
  rules = group["rules"]
  abort("rules missing for #{group["name"]}") unless rules.is_a?(Array) && !rules.empty?
  rules.each do |rule|
    abort("alert name missing in #{group["name"]}") if rule["alert"].to_s.empty?
    abort("expr missing in #{group["name"]}/#{rule["alert"]}") if rule["expr"].to_s.empty?
  end
end
' "${RULES_FILE}"

echo "Prometheus dashboard and alert rule baseline look valid."
