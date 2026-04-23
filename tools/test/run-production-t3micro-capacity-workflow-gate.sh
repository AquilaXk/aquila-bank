#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/production-t3micro-capacity-smoke.yml"

echo "[production-t3micro-workflow] yaml: ${workflow}"
ruby -e "require 'yaml'; YAML.load_file('${workflow}'); puts 'ok'"

echo "[production-t3micro-workflow] trigger and wiring"
ruby <<'RUBY'
require 'yaml'

workflow = YAML.load_file('.github/workflows/production-t3micro-capacity-smoke.yml')
on = workflow['on'] || workflow[true] || abort('workflow on section missing')
abort('workflow_dispatch trigger missing') unless on.key?('workflow_dispatch')
abort('schedule trigger missing') unless on.key?('schedule')
abort('schedule must contain at least one cron entry') unless on.fetch('schedule').is_a?(Array) && !on.fetch('schedule').empty?

inputs = on.fetch('workflow_dispatch').fetch('inputs')
%w[
  soak_repeat
  db_pool_max_size
  server_threads_max
  sse_max_total_sessions
  notification_stream_max
].each do |key|
  abort("workflow_dispatch input missing: #{key}") unless inputs.key?(key)
end

job = workflow.fetch('jobs').fetch('smoke')
abort('workflow concurrency group mismatch') unless workflow.fetch('concurrency').fetch('group') == 'production-t3micro-capacity-smoke'

steps = job.fetch('steps')
step_names = steps.map { |step| step['name'] }
run_name = 'Run production t3.micro capacity smoke'
run_index = step_names.index(run_name) or abort("missing step: #{run_name}")

step = steps.fetch(run_index)
env = step.fetch('env')
run = step.fetch('run')

abort('run step must call production t3.micro capacity smoke script') unless run.include?('tools/test/run-production-t3micro-capacity-smoke.sh')
abort('SOAK_REPEAT must come from workflow input or repository variable') unless env.fetch('SOAK_REPEAT').include?('inputs.soak_repeat') && env.fetch('SOAK_REPEAT').include?('vars.PRODUCTION_T3MICRO_SOAK_REPEAT')
abort('db pool budget must come from input or repository variable') unless env.fetch('PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE').include?('inputs.db_pool_max_size') && env.fetch('PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE').include?('vars.PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE')
abort('server thread budget must come from input or repository variable') unless env.fetch('PRODUCTION_T3MICRO_SERVER_THREADS_MAX').include?('inputs.server_threads_max') && env.fetch('PRODUCTION_T3MICRO_SERVER_THREADS_MAX').include?('vars.PRODUCTION_T3MICRO_SERVER_THREADS_MAX')
abort('sse budget must come from input or repository variable') unless env.fetch('PRODUCTION_T3MICRO_SSE_MAX_TOTAL_SESSIONS').include?('inputs.sse_max_total_sessions') && env.fetch('PRODUCTION_T3MICRO_SSE_MAX_TOTAL_SESSIONS').include?('vars.PRODUCTION_T3MICRO_SSE_MAX_TOTAL_SESSIONS')
abort('notification stream budget must come from input or repository variable') unless env.fetch('PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX').include?('inputs.notification_stream_max') && env.fetch('PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX').include?('vars.PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX')
RUBY

echo "[production-t3micro-workflow] passed"
