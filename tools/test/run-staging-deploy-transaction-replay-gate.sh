#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/staging-deploy.yml"

echo "[staging-replay-gate] yaml: ${workflow}"
ruby -e "require 'yaml'; YAML.load_file('${workflow}'); puts 'ok'"

echo "[staging-replay-gate] step order and env wiring"
ruby <<'RUBY'
require 'yaml'

workflow = YAML.load_file('.github/workflows/staging-deploy.yml')
jobs = workflow.fetch('jobs')
deploy_job = jobs.fetch('deploy-and-verify')
finalize_job = jobs.fetch('finalize-deployment')
steps = deploy_job.fetch('steps')
step_names = steps.map { |step| step['name'] }

replay_name = 'Run transaction replay regression gate'
append_replay_summary_name = 'Append transaction replay summary'
upload_replay_report_name = 'Upload transaction replay report'
smoke_name = 'Run staging post-deploy smoke'
fixture_principal_name = 'Ensure staging fixture principal'
deploy_name = 'Run OCI A1 blue-green deploy locally'
resolver_name = 'Resolve OCI A1 staging database URL'
load_env_name = 'Load OCI A1 staging env'
prerequisite_name = 'Check OCI self-hosted runner prerequisites'

replay_index = step_names.index(replay_name) or abort("missing step: #{replay_name}")
append_replay_summary_index = step_names.index(append_replay_summary_name) or abort("missing step: #{append_replay_summary_name}")
upload_replay_report_index = step_names.index(upload_replay_report_name) or abort("missing step: #{upload_replay_report_name}")
smoke_index = step_names.index(smoke_name) or abort("missing step: #{smoke_name}")
fixture_principal_index = step_names.index(fixture_principal_name) or abort("missing step: #{fixture_principal_name}")
deploy_index = step_names.index(deploy_name) or abort("missing step: #{deploy_name}")
resolver_index = step_names.index(resolver_name) or abort("missing step: #{resolver_name}")
load_env_index = step_names.index(load_env_name) or abort("missing step: #{load_env_name}")
prerequisite_index = step_names.index(prerequisite_name) or abort("missing step: #{prerequisite_name}")

runner_labels = Array(deploy_job.fetch('runs-on'))
abort('deploy job must run on OCI self-hosted runner') unless runner_labels.include?('self-hosted') && runner_labels.include?('oci-a1-staging')
abort('staging env must load before smoke') unless load_env_index < smoke_index
abort('runner prerequisites must run after env load') unless load_env_index < prerequisite_index
abort('DB URL resolver must run after runner prerequisites') unless prerequisite_index < resolver_index
abort('DB URL resolver must run before OCI deploy') unless resolver_index < deploy_index
abort('runner prerequisites must run before OCI deploy') unless prerequisite_index < deploy_index
abort('fixture principal must run after OCI deploy') unless deploy_index < fixture_principal_index
abort('fixture principal must run before smoke') unless fixture_principal_index < smoke_index
abort('replay gate must run after staging smoke') unless smoke_index < replay_index
abort('replay summary must run after replay gate') unless replay_index < append_replay_summary_index
abort('replay report upload must run after replay gate') unless replay_index < upload_replay_report_index

finalize_needs = Array(finalize_job.fetch('needs'))
abort('finalize job must depend on deploy-and-verify') unless finalize_needs.include?('deploy-and-verify')
finalize_steps = finalize_job.fetch('steps')
success_step = finalize_steps.find { |step| step['name'] == 'Mark staging deployment success' } or abort('missing finalize success step')
abort('success status must require deploy-and-verify success') unless success_step.fetch('if').include?("needs.deploy-and-verify.result == 'success'")

load_env_step = steps.fetch(load_env_index)
load_env = load_env_step.fetch('env')
load_run = load_env_step.fetch('run')
replay_step = steps.fetch(replay_index)
run = replay_step.fetch('run')
append_replay_summary_step = steps.fetch(append_replay_summary_index)
upload_replay_report_step = steps.fetch(upload_replay_report_index)

required_keys = %w[
  STAGING_BASE_URL
  STAGING_SMOKE_BASE_URL
  STAGING_REPLAY_TOKEN
  STAGING_OCI_A1_DATABASE_URL
  STAGING_RDS_DATABASE_URL
  HOT_ACCOUNT_ID
  HOT_FROM
  HOT_TO
  COLD_ACCOUNT_ID
  COLD_FROM
  COLD_TO
  STAGING_REPLAY_USER_ID
  STAGING_REPLAY_LOGIN_ID
  STAGING_REPLAY_USER_PASSWORD_HASH
  STAGING_REPLAY_USER_DISPLAY_NAME
  POSTGRES_CONTAINER_NAME
  POSTGRES_NETWORK_ALIAS
  POSTGRES_HOST_BIND
]
required_keys.each do |key|
  abort("load step must persist env: #{key}") unless load_run.include?(key)
end

abort('load step must read only the unified staging env secret') unless load_env.fetch('OCI_A1_STAGING_ENV').include?('secrets.OCI_A1_STAGING_ENV')
abort('load step must source the staging env file') unless load_run.include?('source "${staging_env_path}"')
abort('replay step should not scatter staging env mappings') if replay_step.key?('env')
abort('replay step must call transaction replay script') unless run.include?('tools/ops/transaction-read-model-staging-replay.sh')
abort('replay summary must run even after replay failure') unless append_replay_summary_step.fetch('if').include?('always()')
append_replay_summary_run = append_replay_summary_step.fetch('run')
abort('replay summary must append summary.md to GITHUB_STEP_SUMMARY') unless append_replay_summary_run.include?('build/reports/transaction-staging-replay/summary.md') && append_replay_summary_run.include?('GITHUB_STEP_SUMMARY')
abort('replay report upload must run even after replay failure') unless upload_replay_report_step.fetch('if').include?('always()')
abort('replay report upload must use upload-artifact') unless upload_replay_report_step.fetch('uses') == 'actions/upload-artifact@v4'
upload_with = upload_replay_report_step.fetch('with')
abort('replay report upload name must be stable') unless upload_with.fetch('name') == 'transaction-read-model-staging-replay'
abort('replay report upload path must include replay report directory') unless upload_with.fetch('path') == 'build/reports/transaction-staging-replay/'
abort('replay report upload must ignore missing report files') unless upload_with.fetch('if-no-files-found') == 'ignore'
abort('fixture principal step must call fixture principal script') unless steps.fetch(fixture_principal_index).fetch('run').include?('tools/ops/staging-fixture-principal-bootstrap.sh')
abort('OCI deploy step must call local bluegreen script') unless steps.fetch(deploy_index).fetch('run').include?('ops/deploy/oci/bluegreen-deploy.sh')
resolver_run = steps.fetch(resolver_index).fetch('run')
abort('DB URL resolver step must call resolver script') unless resolver_run.include?('tools/ops/resolve-oci-a1-staging-database-url.sh')
abort('DB URL resolver must mask resolved URL') unless resolver_run.include?('::add-mask::${resolved_database_url}')
abort('DB URL resolver must rewrite staging DB URL in GITHUB_ENV') unless resolver_run.include?('STAGING_OCI_A1_DATABASE_URL=%s')
abort('OCI A1 database URL must come from unified staging env') unless load_run.include?('STAGING_OCI_A1_DATABASE_URL')
abort('Postgres host bind must default to host-local port') unless load_run.include?('POSTGRES_HOST_BIND="${POSTGRES_HOST_BIND:-127.0.0.1:5432}"')
abort('smoke base URL must default to loopback for self-hosted runner') unless load_run.include?('STAGING_SMOKE_BASE_URL="${STAGING_SMOKE_BASE_URL:-${STAGING_LOCAL_BASE_URL:-http://127.0.0.1}}"')
abort('hot account must map from staging replay key') unless load_run.include?('HOT_ACCOUNT_ID="${STAGING_REPLAY_HOT_ACCOUNT_ID')
abort('cold account must map from staging replay key') unless load_run.include?('COLD_ACCOUNT_ID="${STAGING_REPLAY_COLD_ACCOUNT_ID')
RUBY
