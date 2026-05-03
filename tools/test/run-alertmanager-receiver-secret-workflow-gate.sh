#!/usr/bin/env bash
set -euo pipefail

echo "[alertmanager-secret-workflow] yaml: staging-deploy"
ruby -e "require 'yaml'; YAML.load_file('.github/workflows/staging-deploy.yml'); puts 'ok'"

echo "[alertmanager-secret-workflow] yaml: production-promotion"
ruby -e "require 'yaml'; YAML.load_file('.github/workflows/production-promotion.yml'); puts 'ok'"

echo "[alertmanager-secret-workflow] step order and env wiring"
ruby <<'RUBY'
require 'yaml'

def validate_secret_step(workflow_path, job_name, prerequisites, success_step = nil)
  workflow = YAML.load_file(workflow_path)
  steps = workflow.fetch('jobs').fetch(job_name).fetch('steps')
  step_names = steps.map { |step| step['name'] }
  target_name = 'Run Alertmanager receiver secret smoke'
  target_index = step_names.index(target_name) or abort("missing step: #{target_name} in #{workflow_path}")

  prerequisites.each do |name|
    prerequisite_index = step_names.index(name) or abort("missing step: #{name} in #{workflow_path}")
    abort("#{target_name} must run after #{name} in #{workflow_path}") unless prerequisite_index < target_index
  end

  if success_step
    success_index = step_names.index(success_step) or abort("missing step: #{success_step} in #{workflow_path}")
    abort("#{target_name} must run before #{success_step} in #{workflow_path}") unless target_index < success_index
  end

  step = steps.fetch(target_index)
  env = step.fetch('env')
  run = step.fetch('run')

  required_keys = %w[
    ALERTMANAGER_RECEIVER_SECRET_SMOKE_ENVIRONMENT
    ALERTMANAGER_RECEIVER_SLACK_ENABLED
    ALERTMANAGER_RECEIVER_SLACK_WEBHOOK_URL
    ALERTMANAGER_RECEIVER_PAGERDUTY_ENABLED
    ALERTMANAGER_RECEIVER_PAGERDUTY_ROUTING_KEY
    ALERTMANAGER_RECEIVER_WEBHOOK_ENABLED
    ALERTMANAGER_RECEIVER_WEBHOOK_URL
    ALERTMANAGER_RECEIVER_TELEGRAM_ENABLED
    ALERTMANAGER_RECEIVER_TELEGRAM_BOT_TOKEN
    ALERTMANAGER_RECEIVER_TELEGRAM_CHAT_ID
  ]
  required_keys.each do |key|
    abort("missing env #{key} in #{workflow_path}") unless env.key?(key)
  end

  abort("smoke step must call validate-alertmanager-receiver-secrets.sh in #{workflow_path}") unless run.include?('tools/ops/validate-alertmanager-receiver-secrets.sh')
  abort("slack enabled must come from secret in #{workflow_path}") unless env.fetch('ALERTMANAGER_RECEIVER_SLACK_ENABLED').include?('secrets.ALERTMANAGER_RECEIVER_SLACK_ENABLED')
  abort("pagerduty key must come from secret in #{workflow_path}") unless env.fetch('ALERTMANAGER_RECEIVER_PAGERDUTY_ROUTING_KEY').include?('secrets.ALERTMANAGER_RECEIVER_PAGERDUTY_ROUTING_KEY')
  abort("webhook url must come from secret in #{workflow_path}") unless env.fetch('ALERTMANAGER_RECEIVER_WEBHOOK_URL').include?('secrets.ALERTMANAGER_RECEIVER_WEBHOOK_URL')
  abort("telegram enabled must come from secret in #{workflow_path}") unless env.fetch('ALERTMANAGER_RECEIVER_TELEGRAM_ENABLED').include?('secrets.ALERTMANAGER_RECEIVER_TELEGRAM_ENABLED')
  abort("telegram token must come from secret in #{workflow_path}") unless env.fetch('ALERTMANAGER_RECEIVER_TELEGRAM_BOT_TOKEN').include?('secrets.ALERTMANAGER_RECEIVER_TELEGRAM_BOT_TOKEN')
  abort("telegram chat id must come from secret in #{workflow_path}") unless env.fetch('ALERTMANAGER_RECEIVER_TELEGRAM_CHAT_ID').include?('secrets.ALERTMANAGER_RECEIVER_TELEGRAM_CHAT_ID')
end

def validate_unified_env_step(workflow_path, job_name)
  workflow = YAML.load_file(workflow_path)
  steps = workflow.fetch('jobs').fetch(job_name).fetch('steps')
  step_names = steps.map { |step| step['name'] }

  target_name = 'Run Alertmanager receiver secret smoke'
  load_name = 'Load OCI A1 staging env'
  prerequisite_name = 'Check OCI self-hosted runner prerequisites'
  deploy_name = 'Run OCI A1 blue-green deploy locally'

  target_index = step_names.index(target_name) or abort("missing step: #{target_name} in #{workflow_path}")
  load_index = step_names.index(load_name) or abort("missing step: #{load_name} in #{workflow_path}")
  prerequisite_index = step_names.index(prerequisite_name) or abort("missing step: #{prerequisite_name} in #{workflow_path}")
  deploy_index = step_names.index(deploy_name) or abort("missing step: #{deploy_name} in #{workflow_path}")

  abort("#{target_name} must run after #{load_name} in #{workflow_path}") unless load_index < target_index
  abort("#{target_name} must run after #{prerequisite_name} in #{workflow_path}") unless prerequisite_index < target_index
  abort("#{target_name} must run before #{deploy_name} in #{workflow_path}") unless target_index < deploy_index

  load_step = steps.fetch(load_index)
  load_env = load_step.fetch('env')
  load_run = load_step.fetch('run')
  target_step = steps.fetch(target_index)

  required_keys = %w[
    ALERTMANAGER_RECEIVER_SECRET_SMOKE_ENVIRONMENT
    ALERTMANAGER_RECEIVER_SLACK_ENABLED
    ALERTMANAGER_RECEIVER_SLACK_WEBHOOK_URL
    ALERTMANAGER_RECEIVER_PAGERDUTY_ENABLED
    ALERTMANAGER_RECEIVER_PAGERDUTY_ROUTING_KEY
    ALERTMANAGER_RECEIVER_WEBHOOK_ENABLED
    ALERTMANAGER_RECEIVER_WEBHOOK_URL
    ALERTMANAGER_RECEIVER_TELEGRAM_ENABLED
    ALERTMANAGER_RECEIVER_TELEGRAM_BOT_TOKEN
    ALERTMANAGER_RECEIVER_TELEGRAM_CHAT_ID
  ]
  required_keys.each do |key|
    abort("load step must persist env: #{key}") unless load_run.include?(key)
  end

  abort("staging alertmanager config must come from unified staging env") unless load_env.fetch('OCI_A1_STAGING_ENV').include?('secrets.OCI_A1_STAGING_ENV')
  abort("staging alertmanager smoke should not scatter secret mappings") if target_step.key?('env')
  abort("smoke step must call validate-alertmanager-receiver-secrets.sh in #{workflow_path}") unless target_step.fetch('run').include?('tools/ops/validate-alertmanager-receiver-secrets.sh')
end

validate_unified_env_step(
  '.github/workflows/staging-deploy.yml',
  'deploy-and-verify'
)

validate_secret_step(
  '.github/workflows/production-promotion.yml',
  'promote',
  ['Validate checkout SHA'],
  'Create production deployment'
)
RUBY

echo "[alertmanager-secret-workflow] passed"
