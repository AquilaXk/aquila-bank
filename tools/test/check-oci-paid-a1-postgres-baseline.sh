#!/usr/bin/env bash
set -euo pipefail

module_dir="infra/terraform/oci/paid-a1-postgres"

require_file() {
  local path="$1"
  if [[ ! -f "${path}" ]]; then
    echo "required file missing: ${path}" >&2
    exit 1
  fi
}

require_pattern() {
  local pattern="$1"
  local path="$2"
  if ! grep -F -- "${pattern}" "${path}" >/dev/null; then
    echo "required pattern missing in ${path}: ${pattern}" >&2
    exit 1
  fi
}

reject_pattern() {
  local pattern="$1"
  local path="$2"
  if grep -F -- "${pattern}" "${path}" >/dev/null; then
    echo "forbidden pattern found in ${path}: ${pattern}" >&2
    exit 1
  fi
}

echo "[oci-paid-a1-postgres] required files"
for file in \
  versions.tf \
  providers.tf \
  variables.tf \
  locals.tf \
  images.tf \
  network.tf \
  compute.tf \
  storage.tf \
  cloud-init.yaml \
  outputs.tf \
  terraform.tfvars.example \
  README.md; do
  require_file "${module_dir}/${file}"
done

echo "[oci-paid-a1-postgres] terraform sizing contract"
require_pattern 'instance_shape = "VM.Standard.A1.Flex"' "${module_dir}/locals.tf"
require_pattern 'default     = 4' "${module_dir}/variables.tf"
require_pattern 'default     = 24' "${module_dir}/variables.tf"
require_pattern 'default     = 50' "${module_dir}/variables.tf"
require_pattern 'default     = 200' "${module_dir}/variables.tf"
require_pattern 'default     = 10' "${module_dir}/variables.tf"
require_pattern 'oci_core_volume' "${module_dir}/storage.tf"
require_pattern 'size_in_gbs' "${module_dir}/storage.tf"
require_pattern 'var.postgres_data_volume_size_in_gbs' "${module_dir}/storage.tf"
require_pattern 'vpus_per_gb' "${module_dir}/storage.tf"
require_pattern 'var.postgres_data_volume_vpus_per_gb' "${module_dir}/storage.tf"
require_pattern 'oci_core_volume_attachment' "${module_dir}/storage.tf"

echo "[oci-paid-a1-postgres] network security contract"
require_pattern 'ssh_ingress_cidr' "${module_dir}/network.tf"
require_pattern 'http_ingress_cidr' "${module_dir}/network.tf"
require_pattern 'https_ingress_cidr' "${module_dir}/network.tf"
reject_pattern 'max = 5432' "${module_dir}/network.tf"
reject_pattern 'min = 5432' "${module_dir}/network.tf"

echo "[oci-paid-a1-postgres] bootstrap contract"
require_pattern 'postgres:18' "${module_dir}/cloud-init.yaml"
require_pattern '/var/lib/aquila-postgres' "${module_dir}/cloud-init.yaml"
require_pattern '127.0.0.1:5432:5432' "${module_dir}/cloud-init.yaml"
require_pattern 'docker network create aquila-bank-prod' "${module_dir}/cloud-init.yaml"
require_pattern '--network aquila-bank-prod' "${module_dir}/cloud-init.yaml"
require_pattern 'AQUILA_POSTGRES_PASSWORD' "${module_dir}/cloud-init.yaml"
require_pattern 'mkfs.ext4' "${module_dir}/cloud-init.yaml"
require_pattern '/etc/fstab' "${module_dir}/cloud-init.yaml"

echo "[oci-paid-a1-postgres] tfvars example contract"
require_pattern 'region           = "ap-seoul-1"' "${module_dir}/terraform.tfvars.example"
require_pattern 'instance_ocpus' "${module_dir}/terraform.tfvars.example"
require_pattern '= 4' "${module_dir}/terraform.tfvars.example"
require_pattern 'instance_memory_in_gbs' "${module_dir}/terraform.tfvars.example"
require_pattern '= 24' "${module_dir}/terraform.tfvars.example"
require_pattern 'boot_volume_size_in_gbs = 50' "${module_dir}/terraform.tfvars.example"
require_pattern 'postgres_data_volume_size_in_gbs = 200' "${module_dir}/terraform.tfvars.example"
require_pattern 'postgres_data_volume_vpus_per_gb = 10' "${module_dir}/terraform.tfvars.example"
reject_pattern 'ocid1.tenancy.oc1..aaaa' "${module_dir}/terraform.tfvars.example"

echo "[oci-paid-a1-postgres] docs contract"
require_pattern 'VM.Standard.A1.Flex' "${module_dir}/README.md"
require_pattern '4 OCPU / 24GB' "${module_dir}/README.md"
require_pattern '200GB' "${module_dir}/README.md"
require_pattern 'PostgreSQL 18' "${module_dir}/README.md"
require_pattern 'aquila-bank-prod' "${module_dir}/README.md"
require_pattern 'aquila-postgres:5432' "${module_dir}/README.md"
require_pattern '5432' "${module_dir}/README.md"
require_pattern 'public ingress' "${module_dir}/README.md"
require_pattern 'terraform init' "${module_dir}/README.md"
require_pattern 'terraform destroy' "${module_dir}/README.md"

echo "[oci-paid-a1-postgres] root docs contract"
require_pattern 'OCI A1 Flex + self-managed PostgreSQL' "README.md"

echo "[oci-paid-a1-postgres] contract check passed"
