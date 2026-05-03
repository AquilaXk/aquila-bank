#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/bootstrap-production-github-environment.sh"
example="tools/ops/production-github-environment.env.example"
workflow=".github/workflows/production-promotion.yml"

echo "[production-env-bootstrap] syntax"
bash -n "${script}"

echo "[production-env-bootstrap] files"
test -f "${example}"
grep -F "tools/ops/production-github-environment.env.example" .env.example >/dev/null
grep -F "tools/ops/production-github-environment.env" .gitignore >/dev/null
grep -F "Do not commit real token" "${example}" >/dev/null
grep -F "OCI_A1_PRODUCTION_ENV" "${example}" >/dev/null
grep -F "gh secret set" "${script}" >/dev/null
grep -F "gh variable set" "${script}" >/dev/null
grep -F "gh api --method PUT" "${script}" >/dev/null
grep -F -- "-F wait_timer=0" "${script}" >/dev/null
grep -F -- "--vars-only" "${script}" >/dev/null
if grep -F "PRODUCTION_DEPLOY_WEBHOOK_URL" "${example}" "${script}" "${workflow}" >/dev/null; then
  echo "production env bootstrap must not require public deploy webhook URL" >&2
  exit 1
fi
if grep -F "PRODUCTION_DEPLOY_TOKEN" "${example}" "${script}" "${workflow}" >/dev/null; then
  echo "production env bootstrap must not require public deploy hook token" >&2
  exit 1
fi

echo "[production-env-bootstrap] workflow key coverage"
ruby <<'RUBY'
require 'yaml'

workflow = YAML.load_file('.github/workflows/production-promotion.yml')
text_files = [
  File.read('tools/ops/production-github-environment.env.example'),
  File.read('tools/ops/bootstrap-production-github-environment.sh'),
].join("\n")

workflow_text = File.read('.github/workflows/production-promotion.yml')
secrets = workflow_text.scan(/secrets\.([A-Z0-9_]+)/).flatten.uniq.sort
vars = workflow_text.scan(/vars\.([A-Z0-9_]+)/).flatten.uniq.sort

(secrets + vars).each do |key|
  abort("missing production env key in example/script: #{key}") unless text_files.include?(key)
end

abort('production environment missing') unless workflow.dig('jobs', 'promote', 'environment', 'name') == 'production'
abort('no secrets discovered') if secrets.empty?
abort('no vars discovered') if vars.empty?
RUBY

echo "[production-env-bootstrap] print plan"
plan="$("${script}" --print-plan)"
grep -F "[production-env-bootstrap] repo=AquilaXk/aquila-bank environment=production" <<<"${plan}" >/dev/null
grep -F "OCI_A1_PRODUCTION_ENV" <<<"${plan}" >/dev/null
if grep -F "PRODUCTION_DEPLOY_WEBHOOK_URL" <<<"${plan}" >/dev/null; then
  echo "production env bootstrap plan must not include deploy webhook URL" >&2
  exit 1
fi
if grep -F "PRODUCTION_DEPLOY_TOKEN" <<<"${plan}" >/dev/null; then
  echo "production env bootstrap plan must not include deploy hook token" >&2
  exit 1
fi
grep -F "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX" <<<"${plan}" >/dev/null

echo "[production-env-bootstrap] placeholder env refuses writes"
if "${script}" --env-file "${example}" --dry-run >/dev/null 2>&1; then
  echo "placeholder production env unexpectedly passed dry-run validation" >&2
  exit 1
fi

echo "[production-env-bootstrap] vars-only accepts example defaults"
"${script}" --env-file "${example}" --vars-only --dry-run >/dev/null
