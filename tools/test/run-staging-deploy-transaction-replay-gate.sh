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
load_env_name = 'Load OCI A1 staging env'

replay_index = step_names.index(replay_name) or abort("missing step: #{replay_name}")
smoke_index = step_names.index(smoke_name) or abort("missing step: #{smoke_name}")
success_index = step_names.index(success_name) or abort("missing step: #{success_name}")
load_env_index = step_names.index(load_env_name) or abort("missing step: #{load_env_name}")

abort('staging env must load before smoke') unless load_env_index < smoke_index
abort('replay gate must run after staging smoke') unless smoke_index < replay_index
abort('replay gate must run before success status') unless replay_index < success_index

load_env_step = steps.fetch(load_env_index)
load_env = load_env_step.fetch('env')
load_run = load_env_step.fetch('run')
replay_step = steps.fetch(replay_index)
run = replay_step.fetch('run')

required_keys = %w[
  STAGING_BASE_URL
  STAGING_REPLAY_TOKEN
  STAGING_OCI_A1_DATABASE_URL
  STAGING_RDS_DATABASE_URL
  HOT_ACCOUNT_ID
  HOT_FROM
  HOT_TO
  COLD_ACCOUNT_ID
  COLD_FROM
  COLD_TO
]
required_keys.each do |key|
  abort("load step must persist env: #{key}") unless load_run.include?(key)
end

abort('load step must read only the unified staging env secret') unless load_env.fetch('OCI_A1_STAGING_ENV').include?('secrets.OCI_A1_STAGING_ENV')
abort('load step must source the staging env file') unless load_run.include?('source "${staging_env_path}"')
abort('replay step should not scatter staging env mappings') if replay_step.key?('env')
abort('replay step must call transaction replay script') unless run.include?('tools/ops/transaction-read-model-staging-replay.sh')
abort('OCI A1 database URL must come from unified staging env') unless load_run.include?('STAGING_OCI_A1_DATABASE_URL')
abort('hot account must map from staging replay key') unless load_run.include?('HOT_ACCOUNT_ID="${STAGING_REPLAY_HOT_ACCOUNT_ID')
abort('cold account must map from staging replay key') unless load_run.include?('COLD_ACCOUNT_ID="${STAGING_REPLAY_COLD_ACCOUNT_ID')
RUBY
