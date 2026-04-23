#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/staging-deploy.yml"

echo "[staging-replay-gate] yaml: ${workflow}"
ruby -e "require 'yaml'; YAML.load_file('${workflow}'); puts 'ok'"

echo "[staging-replay-gate] step order and env wiring"
ruby <<'RUBY'
require 'yaml'

workflow = YAML.load_file('.github/workflows/staging-deploy.yml')
steps = workflow.fetch('jobs').fetch('deploy').fetch('steps')
step_names = steps.map { |step| step['name'] }

replay_name = 'Run transaction replay regression gate'
smoke_name = 'Run staging post-deploy smoke'
success_name = 'Mark staging deployment success'

replay_index = step_names.index(replay_name) or abort("missing step: #{replay_name}")
smoke_index = step_names.index(smoke_name) or abort("missing step: #{smoke_name}")
success_index = step_names.index(success_name) or abort("missing step: #{success_name}")

abort('replay gate must run after staging smoke') unless smoke_index < replay_index
abort('replay gate must run before success status') unless replay_index < success_index

replay_step = steps.fetch(replay_index)
env = replay_step.fetch('env')
run = replay_step.fetch('run')

required_keys = %w[
  STAGING_BASE_URL
  STAGING_REPLAY_TOKEN
  STAGING_RDS_DATABASE_URL
  HOT_ACCOUNT_ID
  HOT_FROM
  HOT_TO
  COLD_ACCOUNT_ID
  COLD_FROM
  COLD_TO
]
required_keys.each do |key|
  abort("missing env: #{key}") unless env.key?(key)
end

abort('replay step must call transaction replay script') unless run.include?('tools/ops/transaction-read-model-staging-replay.sh')
abort('hot account must come from staging replay secret') unless env.fetch('HOT_ACCOUNT_ID').include?('STAGING_REPLAY_HOT_ACCOUNT_ID')
abort('cold account must come from staging replay secret') unless env.fetch('COLD_ACCOUNT_ID').include?('STAGING_REPLAY_COLD_ACCOUNT_ID')
RUBY
