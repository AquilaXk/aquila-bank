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
POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-aquila-postgres}"
POSTGRES_NETWORK_ALIAS="${POSTGRES_NETWORK_ALIAS:-aquila-postgres}"
POSTGRES_LOG_TAIL_LINES="${POSTGRES_LOG_TAIL_LINES:-120}"
DB_PREFLIGHT_ENABLED="${DB_PREFLIGHT_ENABLED:-true}"
DB_PREFLIGHT_IMAGE="${DB_PREFLIGHT_IMAGE:-postgres:18-alpine}"
DB_PREFLIGHT_TIMEOUT_SECONDS="${DB_PREFLIGHT_TIMEOUT_SECONDS:-10}"
BACKEND_IMAGE="${BACKEND_IMAGE:?BACKEND_IMAGE is required}"
FRONTEND_IMAGE="${FRONTEND_IMAGE:?FRONTEND_IMAGE is required}"
IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG is required}"
GITHUB_ACTOR="${GITHUB_ACTOR:?GITHUB_ACTOR is required}"
GITHUB_TOKEN_B64="${GITHUB_TOKEN_B64:?GITHUB_TOKEN_B64 is required}"
BACKEND_ENV_B64="${BACKEND_ENV_B64:?BACKEND_ENV_B64 is required}"
FRONTEND_ENV_B64="${FRONTEND_ENV_B64:-}"
DOCKER_CONFIG_DIR=""

cleanup_temp() {
  if [[ -n "${DOCKER_CONFIG_DIR}" && -d "${DOCKER_CONFIG_DIR}" ]]; then
    rm -rf "${DOCKER_CONFIG_DIR}"
  fi
}
trap cleanup_temp EXIT

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

prepare_docker_config() {
  DOCKER_CONFIG_DIR="$(mktemp -d)"
  chmod 700 "${DOCKER_CONFIG_DIR}"
  export DOCKER_CONFIG="${DOCKER_CONFIG_DIR}"
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
  local token login_log
  token="$(printf '%s' "${GITHUB_TOKEN_B64}" | base64 -d)"
  login_log="$(mktemp)"

  # Docker credential helper 없는 OCI VM에서도 token은 임시 DOCKER_CONFIG에만 저장하고 종료 시 삭제한다.
  if ! printf '%s' "${token}" | docker login ghcr.io -u "${GITHUB_ACTOR}" --password-stdin >/dev/null 2>"${login_log}"; then
    cat "${login_log}" >&2
    rm -f "${login_log}"
    exit 1
  fi

  grep -Fv "WARNING! Your credentials are stored unencrypted" "${login_log}" \
    | grep -Fv "Configure a credential helper" \
    | grep -Fv "https://docs.docker.com/go/credential-store/" >&2 || true
  rm -f "${login_log}"
}

env_value() {
  local name="$1"
  sed -n "s/^${name}=//p" "${APP_DIR}/env/backend.env" | tail -1
}

postgres_host_requires_container() {
  local db_host="$1"
  [[ "${db_host}" == "${POSTGRES_CONTAINER_NAME}" || "${db_host}" == "${POSTGRES_NETWORK_ALIAS}" ]]
}

postgres_container_running() {
  docker ps --format '{{.Names}}' | grep -qx "${POSTGRES_CONTAINER_NAME}"
}

diagnose_postgres_preflight() {
  log "postgres preflight diagnostics: expected_container=${POSTGRES_CONTAINER_NAME} expected_network=${NETWORK}"
  docker ps -a \
    --filter "name=^/${POSTGRES_CONTAINER_NAME}$" \
    --format 'table {{.Names}}\t{{.Status}}\t{{.Networks}}' || true
  docker network inspect -f 'network containers={{range $id, $container := .Containers}}{{$container.Name}} {{end}}' "${NETWORK}" || true

  if docker inspect "${POSTGRES_CONTAINER_NAME}" >/dev/null 2>&1; then
    docker inspect -f 'postgres networks={{range $name, $_ := .NetworkSettings.Networks}}{{$name}} {{end}}' "${POSTGRES_CONTAINER_NAME}" || true
    docker logs --tail="${POSTGRES_LOG_TAIL_LINES}" "${POSTGRES_CONTAINER_NAME}" || true
  fi
}

require_postgres_container_for_host() {
  local db_host="$1"
  if ! postgres_host_requires_container "${db_host}"; then
    return 0
  fi

  if ! postgres_container_running; then
    log "backend DB host requires PostgreSQL container: host=${db_host} container=${POSTGRES_CONTAINER_NAME}"
    diagnose_postgres_preflight
    exit 1
  fi
}

connect_postgres_container() {
  if ! postgres_container_running; then
    return 0
  fi

  if docker inspect -f '{{json .NetworkSettings.Networks}}' "${POSTGRES_CONTAINER_NAME}" | grep -Fq "\"${NETWORK}\""; then
    return 0
  fi

  log "connect postgres container ${POSTGRES_CONTAINER_NAME} to network ${NETWORK}"
  if ! docker network connect --alias "${POSTGRES_NETWORK_ALIAS}" "${NETWORK}" "${POSTGRES_CONTAINER_NAME}"; then
    log "connect postgres container failed: container=${POSTGRES_CONTAINER_NAME} network=${NETWORK}"
    diagnose_postgres_preflight
    exit 1
  fi
}

preflight_backend_database() {
  if [[ "${DB_PREFLIGHT_ENABLED}" != "true" ]]; then
    log "backend database preflight skipped"
    return 0
  fi

  local jdbc_url db_host db_port db_name db_user db_password host_port
  jdbc_url="$(env_value SPRING_DATASOURCE_URL)"
  if [[ -z "${jdbc_url}" ]]; then
    jdbc_url="$(env_value DB_URL)"
  fi

  db_host="$(env_value DB_HOST)"
  db_port="$(env_value DB_PORT)"
  db_name="$(env_value DB_NAME)"

  if [[ -n "${jdbc_url}" && "${jdbc_url}" == jdbc:postgresql://* ]]; then
    host_port="${jdbc_url#jdbc:postgresql://}"
    host_port="${host_port%%/*}"
    db_name="${jdbc_url#jdbc:postgresql://}"
    db_name="${db_name#*/}"
    db_name="${db_name%%\?*}"
    db_host="${host_port%%:*}"
    if [[ "${host_port}" == *:* ]]; then
      db_port="${host_port##*:}"
    fi
  fi

  db_port="${db_port:-5432}"
  db_user="$(env_value SPRING_DATASOURCE_USERNAME)"
  db_user="${db_user:-$(env_value DB_USERNAME)}"
  db_password="$(env_value SPRING_DATASOURCE_PASSWORD)"
  db_password="${db_password:-$(env_value DB_PASSWORD)}"

  if [[ -z "${db_host}" || -z "${db_name}" || -z "${db_user}" ]]; then
    log "backend database preflight skipped: DB host/name/user not found in backend env"
    return 0
  fi

  require_postgres_container_for_host "${db_host}"
  connect_postgres_container

  if [[ "${db_host}" == "host.docker.internal" && "$(docker ps --format '{{.Names}}' | grep -x "${POSTGRES_CONTAINER_NAME}" || true)" == "${POSTGRES_CONTAINER_NAME}" ]]; then
    log "backend DB host=host.docker.internal detected while ${POSTGRES_CONTAINER_NAME} is a Docker container; use jdbc:postgresql://${POSTGRES_NETWORK_ALIAS}:5432/${db_name}"
  fi

  log "backend database preflight: ${db_user}@${db_host}:${db_port}/${db_name}"
  if ! docker run --rm --network "${NETWORK}" \
    -e PGPASSWORD="${db_password}" \
    "${DB_PREFLIGHT_IMAGE}" \
    pg_isready -h "${db_host}" -p "${db_port}" -U "${db_user}" -d "${db_name}" -t "${DB_PREFLIGHT_TIMEOUT_SECONDS}" >/dev/null; then
    log "backend database preflight failed: ${db_user}@${db_host}:${db_port}/${db_name}"
    diagnose_postgres_preflight
    exit 1
  fi
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
  prepare_docker_config
  docker_login
  connect_postgres_container
  preflight_backend_database

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
