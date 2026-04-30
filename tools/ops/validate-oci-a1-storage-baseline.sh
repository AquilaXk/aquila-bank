#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/ops/validate-oci-a1-storage-baseline.sh [--print-plan]

Environment:
  OCI_A1_STORAGE_MOUNT_PATH      default /
  OCI_A1_STORAGE_MIN_USABLE_GIB  default 190
  OCI_A1_STORAGE_DF_OUTPUT       optional fixture file with `df -BG -P` output
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

mount_path="${OCI_A1_STORAGE_MOUNT_PATH:-/}"
min_usable_gib="${OCI_A1_STORAGE_MIN_USABLE_GIB:-190}"
df_output="${OCI_A1_STORAGE_DF_OUTPUT:-}"

if ! [[ "${min_usable_gib}" =~ ^[1-9][0-9]*$ ]]; then
  echo "OCI_A1_STORAGE_MIN_USABLE_GIB must be a positive integer" >&2
  exit 1
fi

echo "[oci-a1-storage] mount_path=${mount_path}"
echo "[oci-a1-storage] min_usable_gib=${min_usable_gib}"
echo "[oci-a1-storage] df_output=${df_output:-df-command}"

if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ -n "${df_output}" ]]; then
  if [[ ! -s "${df_output}" ]]; then
    echo "OCI_A1_STORAGE_DF_OUTPUT file is missing or empty: ${df_output}" >&2
    exit 1
  fi
  df_data="$(cat "${df_output}")"
else
  if [[ ! -e "${mount_path}" ]]; then
    echo "[oci-a1-storage] status=fail reason=mount_path_missing mount_path=${mount_path}" >&2
    exit 1
  fi
  df_data="$(df -BG -P "${mount_path}")"
fi

data_line="$(tail -n 1 <<<"${df_data}")"
usable_gib="$(awk '{print $2}' <<<"${data_line}" | tr -d 'G')"
available_gib="$(awk '{print $4}' <<<"${data_line}" | tr -d 'G')"
actual_mount="$(awk '{print $6}' <<<"${data_line}")"

if ! [[ "${usable_gib}" =~ ^[0-9]+$ ]]; then
  echo "unable to parse df usable GiB from: ${data_line}" >&2
  exit 1
fi
if ! [[ "${available_gib}" =~ ^[0-9]+$ ]]; then
  echo "unable to parse df available GiB from: ${data_line}" >&2
  exit 1
fi

echo "[oci-a1-storage] actual_mount=${actual_mount}"
echo "[oci-a1-storage] usable_gib=${usable_gib}"
echo "[oci-a1-storage] available_gib=${available_gib}"

if ((usable_gib < min_usable_gib)); then
  echo "[oci-a1-storage] status=fail usable_gib=${usable_gib} min_usable_gib=${min_usable_gib}" >&2
  exit 1
fi

echo "[oci-a1-storage] status=pass"
