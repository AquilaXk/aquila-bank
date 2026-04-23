#!/usr/bin/env bash
set -euo pipefail

echo "[alertmanager-secret-workflow] yaml: staging-deploy"
ruby -e "require 'yaml'; YAML.load_file('.github/workflows/staging-deploy.yml'); puts 'ok'"

echo "[alertmanager-secret-workflow] yaml: production-promotion"
ruby -e "require 'yaml'; YAML.load_file('.github/workflows/production-promotion.yml'); puts 'ok'"

echo "[alertmanager-secret-workflow] step order and env wiring"
ruby <<'RUBY'
require 'yaml'

def validate_step(workflow_path, job_name, prerequisites, success_step = nil)
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
  ]
  required_keys.each do |key|
    abort("missing env #{key} in #{workflow_path}") unless env.key?(key)
  end

  abort("smoke step must call validate-alertmanager-receiver-secrets.sh in #{workflow_path}") unless run.include?('tools/ops/validate-alertmanager-receiver-secrets.sh')
  abort("slack enabled must come from secret in #{workflow_path}") unless env.fetch('ALERTMANAGER_RECEIVER_SLACK_ENABLED').include?('secrets.ALERTMANAGER_RECEIVER_SLACK_ENABLED')
  abort("pagerduty key must come from secret in #{workflow_path}") unless env.fetch('ALERTMANAGER_RECEIVER_PAGERDUTY_ROUTING_KEY').include?('secrets.ALERTMANAGER_RECEIVER_PAGERDUTY_ROUTING_KEY')
  abort("webhook url must come from secret in #{workflow_path}") unless env.fetch('ALERTMANAGER_RECEIVER_WEBHOOK_URL').include?('secrets.ALERTMANAGER_RECEIVER_WEBHOOK_URL')
end

validate_step(
  '.github/workflows/staging-deploy.yml',
  'deploy',
  ['Resolve staging deploy hook configuration'],
  'Create staging deployment'
)

validate_step(
  '.github/workflows/production-promotion.yml',
  'promote',
  ['Validate checkout SHA'],
  'Create production deployment'
)
RUBY

echo "[alertmanager-secret-workflow] passed"
