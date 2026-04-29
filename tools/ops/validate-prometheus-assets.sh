#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DASHBOARD_FILE="${ROOT_DIR}/ops/prometheus/grafana/aquila-bank-overview.json"
RULES_FILE="${ROOT_DIR}/ops/prometheus/rules/aquila-bank-alerts.yml"
PROMETHEUS_FILE="${ROOT_DIR}/ops/prometheus/prometheus.yml"
ALERTMANAGER_FILE="${ROOT_DIR}/ops/prometheus/alertmanager/alertmanager.yml"
GRAFANA_DATASOURCE_FILE="${ROOT_DIR}/ops/prometheus/grafana/provisioning/datasources/prometheus.yml"
GRAFANA_DASHBOARD_PROVIDER_FILE="${ROOT_DIR}/ops/prometheus/grafana/provisioning/dashboards/aquila-bank.yml"

if [[ ! -f "${DASHBOARD_FILE}" ]]; then
  echo "dashboard file is missing: ${DASHBOARD_FILE}" >&2
  exit 1
fi

if [[ ! -f "${RULES_FILE}" ]]; then
  echo "rules file is missing: ${RULES_FILE}" >&2
  exit 1
fi

for provisioning_file in \
  "${PROMETHEUS_FILE}" \
  "${ALERTMANAGER_FILE}" \
  "${GRAFANA_DATASOURCE_FILE}" \
  "${GRAFANA_DASHBOARD_PROVIDER_FILE}"; do
  if [[ ! -f "${provisioning_file}" ]]; then
    echo "provisioning file is missing: ${provisioning_file}" >&2
    exit 1
  fi
done

jq -e '
  .uid == "aquila-bank-overview"
  and (.panels | type == "array" and length >= 8)
  and any(.panels[]?.targets[]?.expr?; contains("aquila_api_admission_requests_total"))
  and any(.panels[]?.targets[]?.expr?; contains("aquila_api_admission_inflight"))
  and any(.panels[]?; .title == "Transaction Read Accepted P95 SLO" and any(.targets[]?.expr?; contains("aquila_transaction_query_latency_seconds_bucket{outcome=\"success\"") and contains("histogram_quantile(0.95")))
  and any(.panels[]?; .title == "Transaction Read Rejected Ratio" and any(.targets[]?.expr?; contains("aquila_api_admission_requests_total{group=\"transaction-read\",outcome=\"rejected\"")))
  and any(.panels[]?; .title == "Transaction Read Inflight and 429" and any(.targets[]?.expr?; contains("aquila_api_admission_inflight{group=\"transaction-read\"")))
  and any(.panels[]?.targets[]?.expr?; contains("aquila_transaction_429_rate"))
  and any(.panels[]?.targets[]?.expr?; contains("aquila_t3micro_saturation_guard_requests_total"))
  and any(.panels[]?.targets[]?.expr?; contains("aquila_t3micro_saturation_guard_saturated"))
' "${DASHBOARD_FILE}" >/dev/null

jq -e '[.panels[] | .targets // [] | .[]? | .expr] | any(.[]; contains("aquila_auth_throttling_reject_count_total"))' \
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

transaction_rules = data["groups"].find { |group| group["name"] == "aquila-bank-transaction" }&.fetch("rules", [])
p95_rule = transaction_rules.find { |rule| rule["alert"] == "AquilaTransactionQueryLatencyP95SloHigh" }
abort("transaction p95 SLO alert missing") unless p95_rule
expr = p95_rule["expr"].to_s
abort("transaction p95 SLO alert must use histogram_quantile(0.95)") unless expr.match?(/histogram_quantile\s*\(\s*0\.95/)
abort("transaction p95 SLO alert must read histogram buckets") unless expr.include?("aquila_transaction_query_latency_seconds_bucket")
abort("transaction p95 SLO alert must keep query_shape labels") unless expr.include?("query_shape")
p99_rule = transaction_rules.find { |rule| rule["alert"] == "AquilaTransactionQueryLatencyP99SloHigh" }
abort("transaction p99 SLO alert missing") unless p99_rule
expr = p99_rule["expr"].to_s
abort("transaction p99 SLO alert must use histogram_quantile(0.99)") unless expr.match?(/histogram_quantile\s*\(\s*0\.99/)
abort("transaction p99 SLO alert must read histogram buckets") unless expr.include?("aquila_transaction_query_latency_seconds_bucket")
abort("transaction p99 SLO alert must keep query_shape labels") unless expr.include?("query_shape")
abort("transaction average latency alert must not remain as SLO") if transaction_rules.any? { |rule| rule["alert"] == "AquilaTransactionQueryLatencyHigh" }

def require_alert(data, alert_name, required_fragments)
  rule = data["groups"].flat_map { |group| group["rules"] }.find { |item| item["alert"] == alert_name }
  abort("#{alert_name} alert missing") unless rule
  expr = rule["expr"].to_s
  required_fragments.each do |fragment|
    abort("#{alert_name} alert must include #{fragment}") unless expr.include?(fragment)
  end
end

{
  "AquilaTransactionRead429BudgetHigh" => ["aquila_transaction_429_rate", "0.10"],
  "AquilaTransactionRead503HardFailDetected" => ["aquila_transaction_503_count", "aquila_transaction_503_rate"],
  "AquilaAuthThrottlingRejectBurstDetected" => ["aquila_auth_throttling_reject_count_total"],
  "AquilaApiAdmissionRejectBurstDetected" => ["aquila_api_admission_requests_total"],
  "AquilaT3MicroSaturationRejectDetected" => [
    "aquila_t3micro_saturation_guard_requests_total",
    "aquila_t3micro_saturation_guard_saturated"
  ],
  "AquilaDbPoolPendingWaitDetected" => ["hikaricp_connections_pending"],
  "AquilaDbPoolActivePressureHigh" => ["hikaricp_connections_active", "hikaricp_connections_max"],
  "AquilaDbQueryTimeoutDetected" => ["aquila_t3micro_saturation_guard_query_timeouts_total"],
  "AquilaPostgresLockWaitDetected" => ["pg_stat_activity_lock_waiting_count"],
  "AquilaPostgresSlowQueryDetected" => ["pg_stat_statements_seconds_total", "pg_stat_statements_calls_total"]
}.each do |alert_name, required_fragments|
  require_alert(data, alert_name, required_fragments)
end
' "${RULES_FILE}"

ruby -e '
require "yaml"

prometheus = YAML.safe_load(File.read(ARGV[0]), permitted_classes: [], aliases: false)
abort("prometheus rule_files missing") unless prometheus["rule_files"].include?("/etc/prometheus/rules/aquila-bank-alerts.yml")
alertmanagers = prometheus.fetch("alerting").fetch("alertmanagers")
targets = alertmanagers.flat_map { |item| item.fetch("static_configs").flat_map { |config| config.fetch("targets") } }
abort("alertmanager target missing") unless targets.include?("alertmanager:9093")
scrape_jobs = prometheus.fetch("scrape_configs").map { |item| item["job_name"] }
abort("backend scrape job missing") unless scrape_jobs.include?("aquila-bank-backend")
abort("postgres exporter scrape job missing") unless scrape_jobs.include?("postgres-exporter")

alertmanager = YAML.safe_load(File.read(ARGV[1]), permitted_classes: [], aliases: false)
route = alertmanager.fetch("route")
abort("alertmanager group_by must include severity") unless route.fetch("group_by").include?("severity")
receiver_names = alertmanager.fetch("receivers").map { |item| item.fetch("name") }
%w[aquila-bank-null aquila-bank-critical aquila-bank-warning].each do |name|
  abort("receiver missing: #{name}") unless receiver_names.include?(name)
end
route_receivers = route.fetch("routes").map { |item| item.fetch("receiver") }
abort("critical route missing") unless route_receivers.include?("aquila-bank-critical")
abort("warning route missing") unless route_receivers.include?("aquila-bank-warning")

datasource = YAML.safe_load(File.read(ARGV[2]), permitted_classes: [], aliases: false)
prometheus_datasource = datasource.fetch("datasources").find { |item| item["uid"] == "aquila-prometheus" }
abort("Grafana Prometheus datasource missing") unless prometheus_datasource
abort("Grafana datasource type must be prometheus") unless prometheus_datasource["type"] == "prometheus"

dashboard_provider = YAML.safe_load(File.read(ARGV[3]), permitted_classes: [], aliases: false)
provider = dashboard_provider.fetch("providers").find { |item| item["name"] == "aquila-bank" }
abort("Grafana dashboard provider missing") unless provider
abort("Grafana dashboard provider path missing") unless provider.fetch("options").fetch("path") == "/var/lib/grafana/dashboards/aquila-bank"
' "${PROMETHEUS_FILE}" "${ALERTMANAGER_FILE}" "${GRAFANA_DATASOURCE_FILE}" "${GRAFANA_DASHBOARD_PROVIDER_FILE}"

echo "Prometheus dashboard, alert rules, provisioning, and routing baseline look valid."
