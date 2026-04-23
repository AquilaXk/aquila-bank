#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/sse-multinode-drain-smoke.yml"

echo "[sse-multinode-workflow] yaml: ${workflow}"
ruby -e "require 'yaml'; YAML.load_file('${workflow}'); puts 'ok'"

echo "[sse-multinode-workflow] trigger and wiring"
ruby <<'RUBY'
require 'yaml'

workflow = YAML.load_file('.github/workflows/sse-multinode-drain-smoke.yml')
on = workflow['on'] || workflow[true] || abort('workflow on section missing')
abort('workflow_dispatch trigger missing') unless on.key?('workflow_dispatch')
abort('schedule trigger missing') unless on.key?('schedule')
abort('schedule must contain at least one cron entry') unless on.fetch('schedule').is_a?(Array) && !on.fetch('schedule').empty?

inputs = on.fetch('workflow_dispatch').fetch('inputs')
%w[drain_grace_seconds reconnect_delay_ms].each do |key|
  abort("workflow_dispatch input missing: #{key}") unless inputs.key?(key)
end

abort('workflow concurrency group mismatch') unless workflow.fetch('concurrency').fetch('group') == 'sse-multinode-drain-smoke'

steps = workflow.fetch('jobs').fetch('smoke').fetch('steps')
step_names = steps.map { |step| step['name'] }
run_name = 'Run SSE multi-node drain smoke'
run_index = step_names.index(run_name) or abort("missing step: #{run_name}")

step = steps.fetch(run_index)
env = step.fetch('env')
run = step.fetch('run')

abort('run step must call sse multi-node drain smoke script') unless run.include?('tools/test/run-sse-multinode-drain-smoke.sh')
abort('drain grace must come from input or repository variable') unless env.fetch('SSE_MULTINODE_DRAIN_GRACE_SECONDS').include?('inputs.drain_grace_seconds') && env.fetch('SSE_MULTINODE_DRAIN_GRACE_SECONDS').include?('vars.SSE_MULTINODE_DRAIN_GRACE_SECONDS')
abort('reconnect delay must come from input or repository variable') unless env.fetch('SSE_RECONNECT_DELAY_MS').include?('inputs.reconnect_delay_ms') && env.fetch('SSE_RECONNECT_DELAY_MS').include?('vars.SSE_RECONNECT_DELAY_MS')
RUBY

echo "[sse-multinode-workflow] passed"
