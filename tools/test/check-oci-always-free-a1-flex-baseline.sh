#!/usr/bin/env bash
set -euo pipefail

module_dir="infra/terraform/oci/always-free-a1-flex"
workflow=".github/workflows/staging-deploy.yml"

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

echo "[oci-always-free-a1-flex] required files"
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

echo "[oci-always-free-a1-flex] compute and storage sizing contract"
require_pattern 'instance_shape = "VM.Standard.A1.Flex"' "${module_dir}/locals.tf"
require_pattern 'default     = 4' "${module_dir}/variables.tf"
require_pattern 'default     = 24' "${module_dir}/variables.tf"
require_pattern 'default     = 50' "${module_dir}/variables.tf"
require_pattern 'default     = 150' "${module_dir}/variables.tf"
require_pattern 'default     = 0' "${module_dir}/variables.tf"
require_pattern 'data_volume_size_in_gbs' "${module_dir}/variables.tf"
require_pattern 'data_volume_vpus_per_gb' "${module_dir}/variables.tf"
require_pattern 'data_volume_mount_path' "${module_dir}/variables.tf"
require_pattern 'boot_volume_size_in_gbs = 50' "${module_dir}/terraform.tfvars.example"
require_pattern 'data_volume_size_in_gbs = 150' "${module_dir}/terraform.tfvars.example"
require_pattern 'data_volume_vpus_per_gb = 0' "${module_dir}/terraform.tfvars.example"
reject_pattern 'boot_volume_size_in_gbs = 200' "${module_dir}/terraform.tfvars.example"

echo "[oci-always-free-a1-flex] block volume contract"
require_pattern 'resource "oci_core_volume" "data"' "${module_dir}/storage.tf"
require_pattern 'size_in_gbs = var.data_volume_size_in_gbs' "${module_dir}/storage.tf"
require_pattern 'vpus_per_gb = var.data_volume_vpus_per_gb' "${module_dir}/storage.tf"
require_pattern 'resource "oci_core_volume_attachment" "data"' "${module_dir}/storage.tf"
require_pattern 'attachment_type = "paravirtualized"' "${module_dir}/storage.tf"

echo "[oci-always-free-a1-flex] bootstrap contract"
require_pattern 'user_data' "${module_dir}/compute.tf"
require_pattern 'cloud-init.yaml' "${module_dir}/compute.tf"
require_pattern 'mkfs.ext4' "${module_dir}/cloud-init.yaml"
require_pattern '/etc/fstab' "${module_dir}/cloud-init.yaml"
require_pattern '/etc/docker/daemon.json' "${module_dir}/cloud-init.yaml"
require_pattern '${data_volume_mount_path}/docker' "${module_dir}/cloud-init.yaml"
require_pattern '/var/lib/aquila-data' "${module_dir}/terraform.tfvars.example"

echo "[oci-always-free-a1-flex] deploy storage gate contract"
require_pattern 'OCI_A1_STORAGE_MOUNT_PATH="${OCI_A1_STORAGE_MOUNT_PATH:-/var/lib/aquila-data}"' "${workflow}"
require_pattern 'OCI_A1_STORAGE_MIN_USABLE_GIB="${OCI_A1_STORAGE_MIN_USABLE_GIB:-140}"' "${workflow}"

echo "[oci-always-free-a1-flex] docs contract"
require_pattern 'Boot Volume: `50GB`' "${module_dir}/README.md"
require_pattern 'Data Block Volume: `150GB`, Lower Cost `0` VPU/GB' "${module_dir}/README.md"
require_pattern 'boot volume과 data block volume 합산 200GB' "${module_dir}/README.md"
require_pattern '기존 200GB boot volume' "${module_dir}/README.md"
require_pattern 'terraform plan' "${module_dir}/README.md"
require_pattern 'terraform apply' "${module_dir}/README.md"

echo "[oci-always-free-a1-flex] contract check passed"
