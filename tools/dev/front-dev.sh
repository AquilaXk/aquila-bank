#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${repo_root}/front"

port="${PORT:-3000}"
echo "[front-dev] starting Next.js dev server on port ${port}" >&2

yarn dev -p "${port}"
