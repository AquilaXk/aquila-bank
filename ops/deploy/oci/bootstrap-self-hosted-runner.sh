#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: export GITHUB_RUNNER_TOKEN; ops/deploy/oci/bootstrap-self-hosted-runner.sh

Environment:
  GITHUB_RUNNER_TOKEN required GitHub self-hosted runner registration token
  GITHUB_REPOSITORY_SLUG default AquilaXk/aquila-bank
  RUNNER_LABELS default self-hosted,oci-a1-staging
  RUNNER_NAME default <hostname>-oci-a1-staging
  RUNNER_USER default github-runner
  RUNNER_HOME default /opt/actions-runner/aquila-bank-staging
  RUNNER_WORK_DIR default _work
  GITHUB_RUNNER_VERSION default latest
  AQUILA_CONFIGURE_PASSWORDLESS_SUDO default true
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if (( EUID != 0 )); then
  exec sudo -E bash "$0" "$@"
fi

token="${GITHUB_RUNNER_TOKEN:-}"
repository_slug="${GITHUB_REPOSITORY_SLUG:-AquilaXk/aquila-bank}"
runner_labels="${RUNNER_LABELS:-self-hosted,oci-a1-staging}"
runner_name="${RUNNER_NAME:-$(hostname)-oci-a1-staging}"
runner_user="${RUNNER_USER:-github-runner}"
runner_home="${RUNNER_HOME:-/opt/actions-runner/aquila-bank-staging}"
runner_work_dir="${RUNNER_WORK_DIR:-_work}"
runner_version="${GITHUB_RUNNER_VERSION:-latest}"
configure_passwordless_sudo="${AQUILA_CONFIGURE_PASSWORDLESS_SUDO:-true}"

fail() {
  echo "[oci-runner-bootstrap] $1" >&2
  exit 1
}

require_non_empty() {
  local name="$1"
  local value="$2"
  [[ -n "${value}" ]] || fail "${name} is required"
}

require_bool() {
  local name="$1"
  local value="$2"
  case "${value}" in
    true|false) ;;
    *) fail "${name} must be true or false: ${value}" ;;
  esac
}

require_non_empty "GITHUB_RUNNER_TOKEN" "${token}"
require_non_empty "GITHUB_REPOSITORY_SLUG" "${repository_slug}"
require_non_empty "RUNNER_LABELS" "${runner_labels}"
require_bool "AQUILA_CONFIGURE_PASSWORDLESS_SUDO" "${configure_passwordless_sudo}"

case "$(uname -m)" in
  aarch64|arm64)
    runner_arch="arm64"
    ;;
  x86_64|amd64)
    runner_arch="x64"
    ;;
  *)
    fail "unsupported runner architecture: $(uname -m)"
    ;;
esac

echo "[oci-runner-bootstrap] installing host packages"
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  ca-certificates \
  curl \
  docker.io \
  gzip \
  jq \
  postgresql-client \
  tar

systemctl enable --now docker.service

if ! id -u "${runner_user}" >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash "${runner_user}"
fi
usermod -aG docker "${runner_user}"

if [[ "${configure_passwordless_sudo}" == "true" ]]; then
  # staging deploy script는 Docker/Nginx/패키지 설치를 수행하므로 runner 사용자에 root sudo를 고정한다.
  sudoers_file="/etc/sudoers.d/aquila-github-runner"
  printf '%s ALL=(root) NOPASSWD:ALL\n' "${runner_user}" >"${sudoers_file}"
  chmod 0440 "${sudoers_file}"
  visudo -cf "${sudoers_file}" >/dev/null
fi

if [[ "${runner_version}" == "latest" ]]; then
  runner_version="$(
    curl -fsSL "https://api.github.com/repos/actions/runner/releases/latest" |
      jq -r '.tag_name | sub("^v"; "")'
  )"
fi
require_non_empty "GITHUB_RUNNER_VERSION" "${runner_version}"

runner_package="actions-runner-linux-${runner_arch}-${runner_version}.tar.gz"
runner_url="https://github.com/actions/runner/releases/download/v${runner_version}/${runner_package}"
temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

install -d -o "${runner_user}" -g "${runner_user}" "${runner_home}"

if [[ ! -x "${runner_home}/bin/Runner.Listener" ]]; then
  echo "[oci-runner-bootstrap] downloading ${runner_package}"
  curl -fsSL "${runner_url}" -o "${temp_dir}/${runner_package}"
  tar -xzf "${temp_dir}/${runner_package}" -C "${runner_home}"
  chown -R "${runner_user}:${runner_user}" "${runner_home}"
fi

repo_url="https://github.com/${repository_slug}"
if [[ ! -f "${runner_home}/.runner" ]]; then
  echo "[oci-runner-bootstrap] configuring runner ${runner_name} for ${repository_slug}"
  sudo -u "${runner_user}" bash -c '
    set -euo pipefail
    cd "$1"
    ./config.sh \
      --unattended \
      --replace \
      --url "$2" \
      --token "$3" \
      --name "$4" \
      --labels "$5" \
      --work "$6"
  ' bash "${runner_home}" "${repo_url}" "${token}" "${runner_name}" "${runner_labels}" "${runner_work_dir}"
else
  echo "[oci-runner-bootstrap] runner already configured at ${runner_home}"
fi

echo "[oci-runner-bootstrap] installing runner service"
cd "${runner_home}"
./svc.sh install "${runner_user}"
./svc.sh start

echo "[oci-runner-bootstrap] installed runner=${runner_name} labels=${runner_labels} home=${runner_home}"
