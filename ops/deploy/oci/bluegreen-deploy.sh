#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  exec sudo -E bash "$0" "$@"
fi

APP_DIR="${APP_DIR:-/opt/aquila-bank}"
NETWORK="${DOCKER_NETWORK:-aquila-bank-prod}"
NGINX_CONTAINER="${NGINX_CONTAINER:-aquila-bank-nginx}"
BACKEND_SLOT_A="${BACKEND_SLOT_A:-aquila-bank-backend-a}"
BACKEND_SLOT_B="${BACKEND_SLOT_B:-aquila-bank-backend-b}"
FRONTEND_SLOT_A="${FRONTEND_SLOT_A:-aquila-bank-front-a}"
FRONTEND_SLOT_B="${FRONTEND_SLOT_B:-aquila-bank-front-b}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
BACKEND_HOST_PORT_A="${BACKEND_HOST_PORT_A:-18080}"
BACKEND_HOST_PORT_B="${BACKEND_HOST_PORT_B:-18081}"
FRONTEND_HOST_PORT_A="${FRONTEND_HOST_PORT_A:-13000}"
FRONTEND_HOST_PORT_B="${FRONTEND_HOST_PORT_B:-13001}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-180}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-3}"
BLUE_DRAIN_SECONDS="${BLUE_DRAIN_SECONDS:-15}"
SERVER_NAME="${NGINX_SERVER_NAME:-_}"
BACKEND_PROXY_HOST="${NGINX_BACKEND_PROXY_HOST:-}"
BACKEND_IMAGE="${BACKEND_IMAGE:?BACKEND_IMAGE is required}"
FRONTEND_IMAGE="${FRONTEND_IMAGE:?FRONTEND_IMAGE is required}"
IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG is required}"
GITHUB_ACTOR="${GITHUB_ACTOR:?GITHUB_ACTOR is required}"
GITHUB_TOKEN_B64="${GITHUB_TOKEN_B64:?GITHUB_TOKEN_B64 is required}"
BACKEND_ENV_B64="${BACKEND_ENV_B64:?BACKEND_ENV_B64 is required}"
FRONTEND_ENV_B64="${FRONTEND_ENV_B64:-}"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

install_runtime() {
  export DEBIAN_FRONTEND=noninteractive

  if ! command -v docker >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1 || ! command -v curl >/dev/null 2>&1; then
    log "install OCI Ubuntu runtime packages"
    apt-get update
    apt-get install -y ca-certificates curl jq docker.io
  fi

  systemctl enable --now docker
}

prepare_layout() {
  mkdir -p "${APP_DIR}/env" "${APP_DIR}/nginx" "${APP_DIR}/state" "${APP_DIR}/logs"
  chmod 750 "${APP_DIR}" "${APP_DIR}/env" "${APP_DIR}/nginx" "${APP_DIR}/state" "${APP_DIR}/logs"
  docker network create "${NETWORK}" >/dev/null 2>&1 || true
}

write_env_files() {
  printf '%s' "${BACKEND_ENV_B64}" | base64 -d >"${APP_DIR}/env/backend.env"
  chmod 600 "${APP_DIR}/env/backend.env"

  if [[ -n "${FRONTEND_ENV_B64}" ]]; then
    printf '%s' "${FRONTEND_ENV_B64}" | base64 -d >"${APP_DIR}/env/frontend.env"
  else
    : >"${APP_DIR}/env/frontend.env"
  fi
  chmod 600 "${APP_DIR}/env/frontend.env"
}

docker_login() {
  local token
  token="$(printf '%s' "${GITHUB_TOKEN_B64}" | base64 -d)"
  printf '%s' "${token}" | docker login ghcr.io -u "${GITHUB_ACTOR}" --password-stdin >/dev/null
}

active_slot() {
  if [[ -s "${APP_DIR}/state/active-slot" ]]; then
    tr -d '[:space:]' <"${APP_DIR}/state/active-slot"
    return
  fi

  if docker ps --format '{{.Names}}' | grep -qx "${BACKEND_SLOT_A}"; then
    printf 'a'
    return
  fi

  if docker ps --format '{{.Names}}' | grep -qx "${BACKEND_SLOT_B}"; then
    printf 'b'
    return
  fi

  printf 'none'
}

slot_name() {
  local kind="$1"
  local slot="$2"
  case "${kind}:${slot}" in
    backend:a) printf '%s' "${BACKEND_SLOT_A}" ;;
    backend:b) printf '%s' "${BACKEND_SLOT_B}" ;;
    frontend:a) printf '%s' "${FRONTEND_SLOT_A}" ;;
    frontend:b) printf '%s' "${FRONTEND_SLOT_B}" ;;
    *) log "invalid slot: ${kind}:${slot}"; exit 1 ;;
  esac
}

slot_host_port() {
  local kind="$1"
  local slot="$2"
  case "${kind}:${slot}" in
    backend:a) printf '%s' "${BACKEND_HOST_PORT_A}" ;;
    backend:b) printf '%s' "${BACKEND_HOST_PORT_B}" ;;
    frontend:a) printf '%s' "${FRONTEND_HOST_PORT_A}" ;;
    frontend:b) printf '%s' "${FRONTEND_HOST_PORT_B}" ;;
    *) log "invalid slot host port: ${kind}:${slot}"; exit 1 ;;
  esac
}

wait_http_ok() {
  local label="$1"
  local url="$2"
  local elapsed=0
  local code

  log "health check ${label}: ${url}"
  while ((elapsed < HEALTH_TIMEOUT_SECONDS)); do
    code="$(curl -sS -o /dev/null -w '%{http_code}' "${url}" || true)"
    if [[ "${code}" == "200" ]]; then
      log "${label} healthy"
      return 0
    fi
    sleep "${HEALTH_INTERVAL_SECONDS}"
    elapsed=$((elapsed + HEALTH_INTERVAL_SECONDS))
  done

  log "${label} health failed: last_status=${code:-none}"
  return 1
}

run_green_slot() {
  local green="$1"
  local backend_name frontend_name backend_host_port frontend_host_port
  backend_name="$(slot_name backend "${green}")"
  frontend_name="$(slot_name frontend "${green}")"
  backend_host_port="$(slot_host_port backend "${green}")"
  frontend_host_port="$(slot_host_port frontend "${green}")"

  docker pull "${BACKEND_IMAGE}:${IMAGE_TAG}"
  docker pull "${FRONTEND_IMAGE}:${IMAGE_TAG}"

  docker rm -f "${backend_name}" "${frontend_name}" >/dev/null 2>&1 || true

  log "start green backend: ${backend_name}"
  docker run -d \
    --name "${backend_name}" \
    --restart unless-stopped \
    --network "${NETWORK}" \
    --add-host host.docker.internal:host-gateway \
    --env-file "${APP_DIR}/env/backend.env" \
    -e TZ=Asia/Seoul \
    -p "127.0.0.1:${backend_host_port}:${BACKEND_PORT}" \
    "${BACKEND_IMAGE}:${IMAGE_TAG}" >/dev/null

  log "start green frontend: ${frontend_name}"
  docker run -d \
    --name "${frontend_name}" \
    --restart unless-stopped \
    --network "${NETWORK}" \
    --add-host host.docker.internal:host-gateway \
    --env-file "${APP_DIR}/env/frontend.env" \
    -e TZ=Asia/Seoul \
    -p "127.0.0.1:${frontend_host_port}:${FRONTEND_PORT}" \
    "${FRONTEND_IMAGE}:${IMAGE_TAG}" >/dev/null

  if ! wait_http_ok "${backend_name}" "http://127.0.0.1:${backend_host_port}/actuator/health"; then
    docker logs --tail=200 "${backend_name}" || true
    docker rm -f "${backend_name}" "${frontend_name}" >/dev/null 2>&1 || true
    exit 1
  fi

  if ! wait_http_ok "${frontend_name}" "http://127.0.0.1:${frontend_host_port}/"; then
    docker logs --tail=200 "${frontend_name}" || true
    docker rm -f "${backend_name}" "${frontend_name}" >/dev/null 2>&1 || true
    exit 1
  fi
}

render_nginx_config() {
  local slot="$1"
  local backend_name frontend_name backend_proxy_host
  backend_name="$(slot_name backend "${slot}")"
  frontend_name="$(slot_name frontend "${slot}")"
  backend_proxy_host="${BACKEND_PROXY_HOST:-${backend_name}}"

  cat <<NGINX
worker_processes auto;

events {
  worker_connections 1024;
}

http {
  upstream aquila_bank_backend {
    server ${backend_name}:${BACKEND_PORT};
    keepalive 16;
  }

  upstream aquila_bank_frontend {
    server ${frontend_name}:${FRONTEND_PORT};
    keepalive 8;
  }

  server {
    listen 80 default_server;
    server_name ${SERVER_NAME};

    proxy_http_version 1.1;
    proxy_connect_timeout 3s;

    location = /api/v1/notifications/stream {
      # SSE 장기 연결은 proxy buffering을 끄고 기존 blue 슬롯을 짧게 drain한다.
      proxy_pass http://aquila_bank_backend;
      proxy_set_header Host ${backend_proxy_host};
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_set_header X-Forwarded-Host \$host;
      proxy_set_header X-Forwarded-Port \$server_port;
      proxy_set_header Connection "";
      proxy_buffering off;
      proxy_request_buffering off;
      proxy_cache off;
      gzip off;
      proxy_next_upstream off;
      proxy_read_timeout 1900s;
      proxy_send_timeout 1900s;
      add_header X-Accel-Buffering no always;
    }

    location ^~ /actuator/health {
      proxy_pass http://aquila_bank_backend;
      proxy_set_header Host ${backend_proxy_host};
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_set_header X-Forwarded-Host \$host;
      proxy_set_header X-Forwarded-Port \$server_port;
      proxy_set_header Connection "";
      proxy_read_timeout 5s;
      proxy_send_timeout 5s;
      access_log off;
    }

    location /api/ {
      proxy_pass http://aquila_bank_backend;
      proxy_set_header Host ${backend_proxy_host};
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_set_header X-Forwarded-Host \$host;
      proxy_set_header X-Forwarded-Port \$server_port;
      proxy_set_header Connection "";
      proxy_read_timeout 30s;
      proxy_send_timeout 30s;
    }

    location / {
      proxy_pass http://aquila_bank_frontend;
      proxy_set_header Host \$host;
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_set_header X-Forwarded-Host \$host;
      proxy_set_header X-Forwarded-Port \$server_port;
      proxy_set_header Connection "";
      proxy_read_timeout 60s;
      proxy_send_timeout 60s;
    }
  }
}
NGINX
}

ensure_nginx_container() {
  if docker ps --format '{{.Names}}' | grep -qx "${NGINX_CONTAINER}"; then
    return 0
  fi

  docker rm -f "${NGINX_CONTAINER}" >/dev/null 2>&1 || true
  log "start nginx entrypoint"
  docker run -d \
    --name "${NGINX_CONTAINER}" \
    --restart unless-stopped \
    --network "${NETWORK}" \
    -p 80:80 \
    -v "${APP_DIR}/nginx/nginx.conf:/etc/nginx/nginx.conf:ro" \
    nginx:1.27-alpine >/dev/null
}

switch_nginx() {
  local green="$1"
  local next_config="${APP_DIR}/nginx/nginx.conf.next"
  local active_config="${APP_DIR}/nginx/nginx.conf"
  local backup_config="${APP_DIR}/nginx/nginx.conf.previous"

  render_nginx_config "${green}" >"${next_config}"
  # Nginx reload 전 동일 Docker network에서 config를 검증해 기존 blue 슬롯을 보존한다.
  docker run --rm --network "${NETWORK}" \
    -v "${next_config}:/etc/nginx/nginx.conf:ro" \
    nginx:1.27-alpine nginx -t

  if [[ -s "${active_config}" ]]; then
    cp "${active_config}" "${backup_config}"
  fi

  mv "${next_config}" "${active_config}"
  ensure_nginx_container

  if ! docker exec "${NGINX_CONTAINER}" nginx -s reload; then
    log "nginx reload failed; restore previous config"
    if [[ -s "${backup_config}" ]]; then
      cp "${backup_config}" "${active_config}"
      docker exec "${NGINX_CONTAINER}" nginx -s reload || true
    fi
    return 1
  fi

  printf '%s\n' "${green}" >"${APP_DIR}/state/active-slot"
}

cleanup_blue() {
  local blue="$1"
  if [[ "${blue}" == "none" ]]; then
    return 0
  fi

  log "drain old blue slot=${blue} seconds=${BLUE_DRAIN_SECONDS}"
  sleep "${BLUE_DRAIN_SECONDS}"
  docker rm -f "$(slot_name backend "${blue}")" "$(slot_name frontend "${blue}")" >/dev/null 2>&1 || true
}

main() {
  local blue green
  install_runtime
  prepare_layout
  write_env_files
  docker_login

  blue="$(active_slot)"
  case "${blue}" in
    a) green="b" ;;
    b) green="a" ;;
    none) green="a" ;;
    *) log "unknown active slot=${blue}"; exit 1 ;;
  esac
  log "blue=${blue} green=${green}"

  run_green_slot "${green}"
  if ! switch_nginx "${green}"; then
    docker rm -f "$(slot_name backend "${green}")" "$(slot_name frontend "${green}")" >/dev/null 2>&1 || true
    exit 1
  fi

  cleanup_blue "${blue}"
  docker image prune -f >/dev/null || true
  log "blue-green deploy complete active=${green}"
}

main "$@"
