#!/usr/bin/env bash
set -euo pipefail

workflows=(
  ".github/workflows/transaction-read-model-staging-replay.yml"
  ".github/workflows/transaction-read-replica-staging-smoke.yml"
)

echo "[transaction-read-workflow-oci-secret-wiring] yaml syntax"
for workflow in "${workflows[@]}"; do
  ruby -e "require 'yaml'; YAML.load_file(ARGV.fetch(0)); puts 'ok'" "${workflow}" >/dev/null
done

echo "[transaction-read-workflow-oci-secret-wiring] unified staging env wiring"
ruby <<'RUBY'
require "yaml"

def assert(condition, message)
  abort(message) unless condition
end

def workflow(path)
  YAML.load_file(path)
end

def only_uses_unified_secret!(path)
  text = File.read(path)
  forbidden = [
    "secrets.STAGING_BASE_URL",
    "secrets.STAGING_REPLAY_TOKEN",
    "secrets.STAGING_RDS_DATABASE_URL",
    "secrets.STAGING_RDS_REPLICA_DATABASE_URL"
  ]
  forbidden.each do |pattern|
    assert(!text.include?(pattern), "#{path} must not directly read #{pattern}")
  end
end

def check_load_step!(path, job_name, run_step_name, required_env_names)
  data = workflow(path)
  job = data.fetch("jobs").fetch(job_name)
  runner_labels = Array(job.fetch("runs-on"))
  assert(runner_labels.include?("self-hosted") && runner_labels.include?("oci-a1-staging"),
    "#{path} #{job_name} must run on OCI A1 self-hosted staging runner")

  steps = job.fetch("steps")
  names = steps.map { |step| step["name"] }
  load_index = names.index("Load OCI A1 staging env")
  run_index = names.index(run_step_name)

  assert(load_index, "#{path} missing Load OCI A1 staging env step")
  assert(run_index, "#{path} missing #{run_step_name} step")
  assert(load_index < run_index, "#{path} must load staging env before #{run_step_name}")

  load_step = steps.fetch(load_index)
  load_env = load_step.fetch("env")
  load_run = load_step.fetch("run")

  assert(load_env.fetch("OCI_A1_STAGING_ENV").include?("secrets.OCI_A1_STAGING_ENV"),
    "#{path} must read only OCI_A1_STAGING_ENV secret")
  assert(load_run.include?('source "${staging_env_path}"'), "#{path} must source staging env file")
  assert(load_run.include?("::add-mask::${value}"), "#{path} must mask exported secret values")
  assert(load_run.include?("GITHUB_ENV"), "#{path} must persist restored env through GITHUB_ENV")
  assert(load_run.include?('STAGING_RDS_DATABASE_URL="${STAGING_RDS_DATABASE_URL:-${STAGING_OCI_A1_DATABASE_URL:-}}"'),
    "#{path} must fallback STAGING_RDS_DATABASE_URL to STAGING_OCI_A1_DATABASE_URL")

  required_env_names.each do |name|
    assert(load_run.include?(name), "#{path} must export #{name}")
  end

  run_step = steps.fetch(run_index)
  run_env = run_step["env"] || {}
  %w[STAGING_BASE_URL STAGING_REPLAY_TOKEN STAGING_RDS_DATABASE_URL STAGING_RDS_REPLICA_DATABASE_URL].each do |name|
    assert(!run_env.key?(name), "#{path} #{run_step_name} must not scatter #{name} env mapping")
  end
end

def check_replay_db_resolver!(path)
  data = workflow(path)
  steps = data.fetch("jobs").fetch("replay").fetch("steps")
  names = steps.map { |step| step["name"] }
  load_index = names.index("Load OCI A1 staging env")
  resolver_index = names.index("Resolve OCI A1 staging database URL")
  replay_index = names.index("Run staging replay")

  assert(resolver_index, "#{path} missing Resolve OCI A1 staging database URL step")
  assert(load_index < resolver_index, "#{path} must resolve DB URL after env load")
  assert(resolver_index < replay_index, "#{path} must resolve DB URL before staging replay")

  load_run = steps.fetch(load_index).fetch("run")
  %w[
    OCI_A1_BACKEND_ENV_B64
    POSTGRES_CONTAINER_NAME
    POSTGRES_NETWORK_ALIAS
    POSTGRES_HOST_BIND
  ].each do |name|
    assert(load_run.include?(name), "#{path} must export #{name} for DB URL resolver")
  end

  resolver_run = steps.fetch(resolver_index).fetch("run")
  assert(resolver_run.include?("tools/ops/resolve-oci-a1-staging-database-url.sh"),
    "#{path} DB URL resolver step must call resolver script")
  assert(resolver_run.include?("::add-mask::${resolved_database_url}"),
    "#{path} DB URL resolver step must mask resolved database URL")
  assert(resolver_run.include?("STAGING_OCI_A1_DATABASE_URL=%s"),
    "#{path} DB URL resolver step must rewrite STAGING_OCI_A1_DATABASE_URL")
end

replay = ".github/workflows/transaction-read-model-staging-replay.yml"
replica = ".github/workflows/transaction-read-replica-staging-smoke.yml"

[replay, replica].each { |path| only_uses_unified_secret!(path) }

check_load_step!(
  replay,
  "replay",
  "Run staging replay",
  %w[
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
    ITERATIONS
    PAGE_LIMIT
    EXPECTED_TOTAL_ROWS
    HOT_P95_THRESHOLD_MS
    COLD_P95_THRESHOLD_MS
    OCI_A1_BACKEND_ENV_B64
    POSTGRES_CONTAINER_NAME
    POSTGRES_NETWORK_ALIAS
    POSTGRES_HOST_BIND
  ]
)

check_replay_db_resolver!(replay)

check_load_step!(
  replica,
  "smoke",
  "Run transaction read replica staging smoke",
  %w[
    STAGING_BASE_URL
    STAGING_REPLAY_TOKEN
    STAGING_RDS_DATABASE_URL
    STAGING_RDS_REPLICA_DATABASE_URL
    TRANSACTION_ARCHIVE_ACCOUNT_ID
    TRANSACTION_ARCHIVE_FROM
    TRANSACTION_ARCHIVE_TO
    PAGE_LIMIT
    REPLICA_LAG_THRESHOLD_MS
  ]
)
RUBY
