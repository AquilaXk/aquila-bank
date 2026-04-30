#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/validate-oci-a1-storage-baseline.sh"
workflow=".github/workflows/staging-deploy.yml"

echo "[oci-a1-storage] shell syntax"
bash -n "${script}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

pass_df="${temp_dir}/pass-df.txt"
fail_df="${temp_dir}/fail-df.txt"

cat >"${pass_df}" <<'DF'
Filesystem     1G-blocks  Used Available Use% Mounted on
/dev/sda1            193G   34G      159G  18% /
DF

cat >"${fail_df}" <<'DF'
Filesystem     1G-blocks  Used Available Use% Mounted on
/dev/sda1            189G   34G      155G  18% /
DF

echo "[oci-a1-storage] print plan"
plan="$(
  OCI_A1_STORAGE_DF_OUTPUT="${pass_df}" \
  OCI_A1_STORAGE_MOUNT_PATH=/ \
  OCI_A1_STORAGE_MIN_USABLE_GIB=190 \
    "${script}" --print-plan
)"
grep -Fx "[oci-a1-storage] mount_path=/" <<<"${plan}" >/dev/null
grep -F "min_usable_gib=190" <<<"${plan}" >/dev/null
grep -F "df_output=${pass_df}" <<<"${plan}" >/dev/null

echo "[oci-a1-storage] default root mount"
default_plan="$(
  OCI_A1_STORAGE_DF_OUTPUT="${pass_df}" \
    "${script}" --print-plan
)"
grep -Fx "[oci-a1-storage] mount_path=/" <<<"${default_plan}" >/dev/null
grep -F "min_usable_gib=190" <<<"${default_plan}" >/dev/null

echo "[oci-a1-storage] pass baseline"
output="$(
  OCI_A1_STORAGE_DF_OUTPUT="${pass_df}" \
    "${script}"
)"
grep -F "usable_gib=193" <<<"${output}" >/dev/null
grep -F "status=pass" <<<"${output}" >/dev/null

echo "[oci-a1-storage] fail drift"
if OCI_A1_STORAGE_DF_OUTPUT="${fail_df}" \
  OCI_A1_STORAGE_MOUNT_PATH=/ \
  OCI_A1_STORAGE_MIN_USABLE_GIB=190 \
    "${script}" >/dev/null 2>&1; then
  echo "189GiB storage drift unexpectedly passed" >&2
  exit 1
fi

echo "[oci-a1-storage] fail missing mount path"
missing_error="$(
  OCI_A1_STORAGE_MOUNT_PATH="${temp_dir}/missing-mount" \
  OCI_A1_STORAGE_MIN_USABLE_GIB=190 \
    "${script}" 2>&1 >/dev/null || true
)"
grep -F "reason=mount_path_missing" <<<"${missing_error}" >/dev/null

echo "[oci-a1-storage] workflow contract"
grep -F "Validate OCI A1 storage baseline" "${workflow}" >/dev/null
grep -F "tools/ops/validate-oci-a1-storage-baseline.sh" "${workflow}" >/dev/null
grep -F "OCI_A1_STORAGE_MOUNT_PATH" "${workflow}" >/dev/null
grep -F "OCI_A1_STORAGE_MIN_USABLE_GIB" "${workflow}" >/dev/null
grep -F 'OCI_A1_STORAGE_MIN_USABLE_GIB="${OCI_A1_STORAGE_MIN_USABLE_GIB:-190}"' "${workflow}" >/dev/null

echo "[oci-a1-storage] invalid input fails"
if OCI_A1_STORAGE_MIN_USABLE_GIB=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "zero min usable unexpectedly succeeded" >&2
  exit 1
fi
