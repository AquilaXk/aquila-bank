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
POSTGRES_BOOTSTRAP_ENABLED="${POSTGRES_BOOTSTRAP_ENABLED:-true}"
POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:18}"
POSTGRES_DATA_VOLUME="${POSTGRES_DATA_VOLUME:-aquila-postgres-data}"
POSTGRES_HOST_BIND="${POSTGRES_HOST_BIND:-127.0.0.1:5432}"
POSTGRES_STARTUP_TIMEOUT_SECONDS="${POSTGRES_STARTUP_TIMEOUT_SECONDS:-120}"
KAFKA_CONTAINER_NAME="${KAFKA_CONTAINER_NAME:-aquila-kafka}"
KAFKA_NETWORK_ALIAS="${KAFKA_NETWORK_ALIAS:-kafka}"
KAFKA_LOG_TAIL_LINES="${KAFKA_LOG_TAIL_LINES:-120}"
KAFKA_IMAGE="${KAFKA_IMAGE:-bitnamilegacy/kafka:4.0.0-debian-12-r10}"
KAFKA_DATA_VOLUME="${KAFKA_DATA_VOLUME:-aquila-kafka-data}"
KAFKA_HOST_BIND="${KAFKA_HOST_BIND:-127.0.0.1:9092}"
KAFKA_STARTUP_TIMEOUT_SECONDS="${KAFKA_STARTUP_TIMEOUT_SECONDS:-120}"
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
OCI_A1_CAPACITY_PROFILE_ENABLED="${OCI_A1_CAPACITY_PROFILE_ENABLED:-true}"
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

backend_spring_profiles_active() {
  local profiles
  profiles="$(env_value SPRING_PROFILES_ACTIVE)"
  profiles="${profiles:-prod}"

  case "${OCI_A1_CAPACITY_PROFILE_ENABLED}" in
    true) ;;
    false)
      printf '%s\n' "${profiles}"
      return
      ;;
    *) log "OCI_A1_CAPACITY_PROFILE_ENABLED must be true or false"; exit 1 ;;
  esac

  case ",${profiles}," in
    *,oci-a1,*) printf '%s\n' "${profiles}" ;;
    *) printf '%s,oci-a1\n' "${profiles}" ;;
  esac
}

backend_capacity_profile_env_args() {
  if [[ "${OCI_A1_CAPACITY_PROFILE_ENABLED}" != "true" ]]; then
    return 0
  fi

  # env-file에 남은 legacy OPS_* 값을 OCI A1 profile 값으로 덮어 adaptive bounds 충돌을 막습니다.
  printf '%s\n' \
    "-e" "OCI_A1_DB_MAX_LIFETIME_MS=${OCI_A1_DB_MAX_LIFETIME_MS:-45000}" \
    "-e" "OCI_A1_DB_KEEPALIVE_TIME_MS=${OCI_A1_DB_KEEPALIVE_TIME_MS:-30000}" \
    "-e" "OCI_A1_DB_IDLE_IN_TX_TIMEOUT_MS=${OCI_A1_DB_IDLE_IN_TX_TIMEOUT_MS:-300000}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=${OCI_A1_TRANSACTION_READ_ADMISSION_MAX:-10}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_RETRY_AFTER_SECONDS=${OCI_A1_TRANSACTION_READ_ADMISSION_RETRY_AFTER_SECONDS:-0}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_MIN=${OCI_A1_TRANSACTION_READ_ADMISSION_MIN:-6}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_MAX=${OCI_A1_TRANSACTION_READ_ADMISSION_ADAPTIVE_MAX:-12}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_INCREASE_EVERY_SUCCESSES=${OCI_A1_TRANSACTION_READ_ADMISSION_INCREASE_EVERY_SUCCESSES:-32}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_DECREASE_ON_REJECTIONS=${OCI_A1_TRANSACTION_READ_ADMISSION_DECREASE_ON_REJECTIONS:-1}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_REJECTION_WINDOW_SIZE=${OCI_A1_TRANSACTION_READ_ADMISSION_REJECTION_WINDOW_SIZE:-32}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_DECREASE_REJECTION_RATIO=${OCI_A1_TRANSACTION_READ_ADMISSION_DECREASE_REJECTION_RATIO:-0.45}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_DECREASE_COOLDOWN_SECONDS=${OCI_A1_TRANSACTION_READ_ADMISSION_DECREASE_COOLDOWN_SECONDS:-2}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_RECOVERY_STEP=${OCI_A1_TRANSACTION_READ_ADMISSION_RECOVERY_STEP:-1}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_LOW_SATURATION_INCREASE_EVERY_SUCCESSES=${OCI_A1_TRANSACTION_READ_ADMISSION_LOW_SATURATION_INCREASE_EVERY_SUCCESSES:-16}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_LOW_SATURATION_RECOVERY_STEP=${OCI_A1_TRANSACTION_READ_ADMISSION_LOW_SATURATION_RECOVERY_STEP:-2}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_LOW_SATURATION_MAX_IN_FLIGHT=${OCI_A1_TRANSACTION_READ_ADMISSION_LOW_SATURATION_MAX_IN_FLIGHT:-1}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_HOT_MAX=${OCI_A1_TRANSACTION_READ_HOT_ADMISSION_MAX:-${OCI_A1_TRANSACTION_READ_ADMISSION_MAX:-10}}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_HOT_ADAPTIVE_MIN=${OCI_A1_TRANSACTION_READ_HOT_ADMISSION_MIN:-${OCI_A1_TRANSACTION_READ_ADMISSION_MIN:-6}}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_HOT_ADAPTIVE_MAX=${OCI_A1_TRANSACTION_READ_HOT_ADMISSION_ADAPTIVE_MAX:-${OCI_A1_TRANSACTION_READ_ADMISSION_ADAPTIVE_MAX:-12}}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ARCHIVE_MAX=${OCI_A1_TRANSACTION_READ_ARCHIVE_ADMISSION_MAX:-6}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ARCHIVE_ADAPTIVE_MIN=${OCI_A1_TRANSACTION_READ_ARCHIVE_ADMISSION_MIN:-5}" \
    "-e" "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ARCHIVE_ADAPTIVE_MAX=${OCI_A1_TRANSACTION_READ_ARCHIVE_ADMISSION_ADAPTIVE_MAX:-10}"
}

postgres_host_requires_container() {
  local db_host="$1"
  [[ "${db_host}" == "${POSTGRES_CONTAINER_NAME}" || "${db_host}" == "${POSTGRES_NETWORK_ALIAS}" ]]
}

postgres_container_running() {
  docker ps --format '{{.Names}}' | grep -qx "${POSTGRES_CONTAINER_NAME}"
}

postgres_container_exists() {
  docker ps -a --format '{{.Names}}' | grep -qx "${POSTGRES_CONTAINER_NAME}"
}

kafka_container_running() {
  docker ps --format '{{.Names}}' | grep -qx "${KAFKA_CONTAINER_NAME}"
}

kafka_container_exists() {
  docker ps -a --format '{{.Names}}' | grep -qx "${KAFKA_CONTAINER_NAME}"
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

write_postgres_env_file() {
  local db_name="$1"
  local db_user="$2"
  local db_password="$3"
  local postgres_env="${APP_DIR}/env/postgres.env"

  if [[ -z "${db_password}" ]]; then
    log "postgres bootstrap requires DB password in backend env"
    diagnose_postgres_preflight
    exit 1
  fi

  umask 077
  {
    printf 'POSTGRES_DB=%s\n' "${db_name}"
    printf 'POSTGRES_USER=%s\n' "${db_user}"
    printf 'POSTGRES_PASSWORD=%s\n' "${db_password}"
    printf 'TZ=Asia/Seoul\n'
  } >"${postgres_env}"
  chmod 600 "${postgres_env}"
}

write_postgres_systemd_env_file() {
  local db_name="$1"
  local db_user="$2"
  local db_password="$3"

  if [[ -z "${db_password}" ]]; then
    log "postgres systemd bootstrap requires DB password in backend env"
    diagnose_postgres_preflight
    exit 1
  fi

  umask 077
  {
    printf 'AQUILA_POSTGRES_DB=%s\n' "${db_name}"
    printf 'AQUILA_POSTGRES_USER=%s\n' "${db_user}"
    printf 'AQUILA_POSTGRES_PASSWORD=%s\n' "${db_password}"
  } >/etc/aquila-postgres.env
  chmod 600 /etc/aquila-postgres.env
}

start_postgres_systemd_service() {
  local db_name="$1"
  local db_user="$2"
  local db_password="$3"

  if ! systemctl cat aquila-postgres.service >/dev/null 2>&1; then
    return 1
  fi

  log "bootstrap postgres container via systemd service aquila-postgres.service"
  write_postgres_systemd_env_file "${db_name}" "${db_user}" "${db_password}"
  if ! systemctl enable --now aquila-postgres.service; then
    log "postgres systemd service failed to start"
    systemctl status aquila-postgres.service --no-pager || true
    diagnose_postgres_preflight
    exit 1
  fi
  return 0
}

start_postgres_docker_container() {
  local db_name="$1"
  local db_user="$2"
  local db_password="$3"

  log "bootstrap postgres container with docker volume=${POSTGRES_DATA_VOLUME}"
  write_postgres_env_file "${db_name}" "${db_user}" "${db_password}"
  docker volume create "${POSTGRES_DATA_VOLUME}" >/dev/null
  if ! docker run -d \
    --name "${POSTGRES_CONTAINER_NAME}" \
    --restart unless-stopped \
    --pull missing \
    --network "${NETWORK}" \
    --network-alias "${POSTGRES_NETWORK_ALIAS}" \
    --label com.aquilabank.runtime=oci-a1 \
    --label com.aquilabank.service=postgres \
    -p "${POSTGRES_HOST_BIND}:5432" \
    --env-file "${APP_DIR}/env/postgres.env" \
    -v "${POSTGRES_DATA_VOLUME}:/var/lib/postgresql" \
    "${POSTGRES_IMAGE}" >/dev/null; then
    log "postgres docker bootstrap failed"
    diagnose_postgres_preflight
    exit 1
  fi
}

ensure_postgres_container_for_host() {
  local db_host="$1"
  local db_name="$2"
  local db_user="$3"
  local db_password="$4"

  if ! postgres_host_requires_container "${db_host}"; then
    return 0
  fi

  if postgres_container_running; then
    connect_postgres_container
    return 0
  fi

  if postgres_container_exists; then
    log "start existing postgres container ${POSTGRES_CONTAINER_NAME}"
    if ! docker start "${POSTGRES_CONTAINER_NAME}" >/dev/null; then
      log "existing postgres container failed to start"
      diagnose_postgres_preflight
      exit 1
    fi
    connect_postgres_container
    return 0
  fi

  if [[ "${POSTGRES_BOOTSTRAP_ENABLED}" != "true" ]]; then
    require_postgres_container_for_host "${db_host}"
    return 0
  fi

  if ! start_postgres_systemd_service "${db_name}" "${db_user}" "${db_password}"; then
    start_postgres_docker_container "${db_name}" "${db_user}" "${db_password}"
  fi
  connect_postgres_container
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

diagnose_kafka() {
  log "kafka diagnostics: expected_container=${KAFKA_CONTAINER_NAME} expected_network=${NETWORK}"
  docker ps -a \
    --filter "name=^/${KAFKA_CONTAINER_NAME}$" \
    --format 'table {{.Names}}\t{{.Status}}\t{{.Networks}}' || true
  docker network inspect -f 'network containers={{range $id, $container := .Containers}}{{$container.Name}} {{end}}' "${NETWORK}" || true

  if docker inspect "${KAFKA_CONTAINER_NAME}" >/dev/null 2>&1; then
    docker inspect -f 'kafka networks={{range $name, $_ := .NetworkSettings.Networks}}{{$name}} {{end}}' "${KAFKA_CONTAINER_NAME}" || true
    docker logs --tail="${KAFKA_LOG_TAIL_LINES}" "${KAFKA_CONTAINER_NAME}" || true
  fi
}

connect_kafka_container() {
  if ! kafka_container_running; then
    return 0
  fi

  if docker inspect -f '{{json .NetworkSettings.Networks}}' "${KAFKA_CONTAINER_NAME}" | grep -Fq "\"${NETWORK}\""; then
    return 0
  fi

  log "connect kafka container ${KAFKA_CONTAINER_NAME} to network ${NETWORK}"
  if ! docker network connect --alias "${KAFKA_NETWORK_ALIAS}" "${NETWORK}" "${KAFKA_CONTAINER_NAME}"; then
    log "connect kafka container failed: container=${KAFKA_CONTAINER_NAME} network=${NETWORK}"
    diagnose_kafka
    exit 1
  fi
}

start_kafka_docker_container() {
  log "bootstrap Kafka container with docker volume=${KAFKA_DATA_VOLUME}"
  docker volume create "${KAFKA_DATA_VOLUME}" >/dev/null
  if ! docker run -d \
    --name "${KAFKA_CONTAINER_NAME}" \
    --restart unless-stopped \
    --pull missing \
    --network "${NETWORK}" \
    --network-alias "${KAFKA_NETWORK_ALIAS}" \
    --label com.aquilabank.runtime=oci-a1 \
    --label com.aquilabank.service=kafka \
    -p "${KAFKA_HOST_BIND}:9092" \
    -e TZ=Asia/Seoul \
    -e ALLOW_PLAINTEXT_LISTENER=yes \
    -e KAFKA_CFG_NODE_ID=0 \
    -e KAFKA_CFG_PROCESS_ROLES=controller,broker \
    -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
    -e KAFKA_CFG_ADVERTISED_LISTENERS="PLAINTEXT://${KAFKA_NETWORK_ALIAS}:9092" \
    -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
    -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
    -e "KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=0@${KAFKA_NETWORK_ALIAS}:9093" \
    -e KAFKA_CFG_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
    -e KAFKA_CFG_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
    -e KAFKA_CFG_MIN_INSYNC_REPLICAS=1 \
    -e KAFKA_CFG_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
    -e KAFKA_CFG_TRANSACTION_STATE_LOG_MIN_ISR=1 \
    -e KAFKA_CFG_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
    -e KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE=false \
    -e KAFKA_CFG_NUM_PARTITIONS=1 \
    -e KAFKA_KRAFT_CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk \
    -v "${KAFKA_DATA_VOLUME}:/bitnami/kafka" \
    "${KAFKA_IMAGE}" >/dev/null; then
    log "Kafka docker bootstrap failed"
    diagnose_kafka
    exit 1
  fi
}

check_kafka_ready() {
  docker exec "${KAFKA_CONTAINER_NAME}" \
    /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list >/dev/null
}

wait_kafka_ready() {
  local elapsed=0

  while (( elapsed < KAFKA_STARTUP_TIMEOUT_SECONDS )); do
    if check_kafka_ready; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done

  return 1
}

ensure_kafka_container_for_backend() {
  if kafka_container_running; then
    connect_kafka_container
  elif kafka_container_exists; then
    log "start existing Kafka container ${KAFKA_CONTAINER_NAME}"
    if ! docker start "${KAFKA_CONTAINER_NAME}" >/dev/null; then
      log "existing Kafka container failed to start"
      diagnose_kafka
      exit 1
    fi
    connect_kafka_container
  else
    start_kafka_docker_container
  fi

  if ! wait_kafka_ready; then
    log "backend Kafka bootstrap requires Kafka container: container=${KAFKA_CONTAINER_NAME} alias=${KAFKA_NETWORK_ALIAS}"
    diagnose_kafka
    exit 1
  fi
}

check_backend_database_ready() {
  local db_host="$1"
  local db_port="$2"
  local db_name="$3"
  local db_user="$4"
  local db_password="$5"

  docker run --rm --network "${NETWORK}" \
    -e PGPASSWORD="${db_password}" \
    "${DB_PREFLIGHT_IMAGE}" \
    pg_isready -h "${db_host}" -p "${db_port}" -U "${db_user}" -d "${db_name}" -t "${DB_PREFLIGHT_TIMEOUT_SECONDS}" >/dev/null
}

wait_backend_database_ready() {
  local db_host="$1"
  local db_port="$2"
  local db_name="$3"
  local db_user="$4"
  local db_password="$5"
  local elapsed=0

  while (( elapsed < POSTGRES_STARTUP_TIMEOUT_SECONDS )); do
    if check_backend_database_ready "${db_host}" "${db_port}" "${db_name}" "${db_user}" "${db_password}"; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done

  return 1
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

  ensure_postgres_container_for_host "${db_host}" "${db_name}" "${db_user}" "${db_password}"

  if [[ "${db_host}" == "host.docker.internal" && "$(docker ps --format '{{.Names}}' | grep -x "${POSTGRES_CONTAINER_NAME}" || true)" == "${POSTGRES_CONTAINER_NAME}" ]]; then
    log "backend DB host=host.docker.internal detected while ${POSTGRES_CONTAINER_NAME} is a Docker container; use jdbc:postgresql://${POSTGRES_NETWORK_ALIAS}:5432/${db_name}"
  fi

  log "backend database preflight: ${db_user}@${db_host}:${db_port}/${db_name}"
  if ! wait_backend_database_ready "${db_host}" "${db_port}" "${db_name}" "${db_user}" "${db_password}"; then
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
  local backend_name frontend_name backend_host_port frontend_host_port backend_profiles
  local -a backend_capacity_env_args
  backend_name="$(slot_name backend "${green}")"
  frontend_name="$(slot_name frontend "${green}")"
  backend_host_port="$(slot_host_port backend "${green}")"
  frontend_host_port="$(slot_host_port frontend "${green}")"
  backend_profiles="$(backend_spring_profiles_active)"
  mapfile -t backend_capacity_env_args < <(backend_capacity_profile_env_args)

  docker pull "${BACKEND_IMAGE}:${IMAGE_TAG}"
  docker pull "${FRONTEND_IMAGE}:${IMAGE_TAG}"

  docker rm -f "${backend_name}" "${frontend_name}" >/dev/null 2>&1 || true

  log "start green backend: ${backend_name} profiles=${backend_profiles}"
  docker run -d \
    --name "${backend_name}" \
    --restart unless-stopped \
    --network "${NETWORK}" \
    --label com.aquilabank.runtime=oci-a1 \
    --label com.aquilabank.service=backend \
    --label "com.aquilabank.slot=${green}" \
    --add-host host.docker.internal:host-gateway \
    --env-file "${APP_DIR}/env/backend.env" \
    -e SPRING_PROFILES_ACTIVE="${backend_profiles}" \
    "${backend_capacity_env_args[@]}" \
    -e TZ=Asia/Seoul \
    -p "127.0.0.1:${backend_host_port}:${BACKEND_PORT}" \
    "${BACKEND_IMAGE}:${IMAGE_TAG}" >/dev/null

  log "start green frontend: ${frontend_name}"
  docker run -d \
    --name "${frontend_name}" \
    --restart unless-stopped \
    --network "${NETWORK}" \
    --label com.aquilabank.runtime=oci-a1 \
    --label com.aquilabank.service=frontend \
    --label "com.aquilabank.slot=${green}" \
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

validate_nginx_real_ip_header() {
  local header="$1"
  case "${header}" in
    X-Forwarded-For|X-Real-IP) ;;
    *) log "NGINX_REAL_IP_HEADER must be X-Forwarded-For or X-Real-IP: ${header}"; exit 1 ;;
  esac
}

render_nginx_real_ip_trusted_proxy_lines() {
  local proxies_csv="$1"
  local lines=""
  local raw_proxy proxy

  if [[ -z "${proxies_csv}" || "${proxies_csv}" == "none" ]]; then
    printf '  # NGINX_REAL_IP_TRUSTED_PROXIES disabled: limiter key stays on TCP peer address.\n'
    return
  fi

  IFS=',' read -r -a proxies <<<"${proxies_csv}"
  for raw_proxy in "${proxies[@]}"; do
    proxy="$(printf '%s' "${raw_proxy}" | xargs)"
    if [[ -z "${proxy}" ]]; then
      continue
    fi
    if ! [[ "${proxy}" =~ ^[0-9A-Fa-f:.\/]+$ ]]; then
      log "NGINX_REAL_IP_TRUSTED_PROXIES contains an invalid IP/CIDR: ${proxy}"
      exit 1
    fi
    lines="${lines}  set_real_ip_from ${proxy};\n"
  done

  if [[ -z "${lines}" ]]; then
    printf '  # NGINX_REAL_IP_TRUSTED_PROXIES empty: limiter key stays on TCP peer address.\n'
    return
  fi

  printf '%b' "${lines}"
}

render_nginx_limit_req_mode() {
  local delay="$1"
  if ! [[ "${delay}" =~ ^[0-9]+$ ]]; then
    log "transaction read Nginx delay must be a non-negative integer: ${delay}"
    exit 1
  fi
  if [[ "${delay}" == "0" ]]; then
    printf 'nodelay'
    return
  fi
  printf 'delay=%s' "${delay}"
}

render_nginx_config() {
  local slot="$1"
  local backend_name frontend_name backend_proxy_host edge_retry_after_seconds edge_retry_after_millis edge_retry_jitter_millis
  local backend_api_keepalive_timeout_seconds
  local real_ip_header real_ip_trusted_proxies real_ip_trusted_proxy_lines
  local transaction_read_budget_profile transaction_read_profile_hot_rate_rps transaction_read_profile_archive_rate_rps
  local transaction_read_profile_hot_burst transaction_read_profile_archive_burst transaction_read_profile_hot_delay transaction_read_profile_archive_delay
  local transaction_read_hot_rate_rps transaction_read_archive_rate_rps transaction_read_hot_burst transaction_read_archive_burst
  local transaction_read_hot_delay transaction_read_archive_delay transaction_read_hot_limit_mode transaction_read_archive_limit_mode
  backend_name="$(slot_name backend "${slot}")"
  frontend_name="$(slot_name frontend "${slot}")"
  backend_proxy_host="${BACKEND_PROXY_HOST:-${backend_name}}"
  backend_api_keepalive_timeout_seconds="${NGINX_BACKEND_API_KEEPALIVE_TIMEOUT_SECONDS:-2}"
  edge_retry_after_seconds="${NGINX_EDGE_RETRY_AFTER_SECONDS:-1}"
  edge_retry_after_millis="${NGINX_EDGE_RETRY_AFTER_MILLIS:-150}"
  edge_retry_jitter_millis="${NGINX_EDGE_RETRY_JITTER_MILLIS:-100}"
  real_ip_header="${NGINX_REAL_IP_HEADER:-X-Forwarded-For}"
  real_ip_trusted_proxies="${NGINX_REAL_IP_TRUSTED_PROXIES:-${OCI_A1_NGINX_REAL_IP_TRUSTED_PROXIES:-10.60.0.0/16}}"
  validate_nginx_real_ip_header "${real_ip_header}"
  real_ip_trusted_proxy_lines="$(render_nginx_real_ip_trusted_proxy_lines "${real_ip_trusted_proxies}")"
  transaction_read_budget_profile="${OCI_A1_TRANSACTION_READ_BUDGET_PROFILE:-${NGINX_TRANSACTION_READ_BUDGET_PROFILE:-burst80}}"
  case "${transaction_read_budget_profile}" in
    burst80)
      transaction_read_profile_hot_rate_rps=256
      transaction_read_profile_archive_rate_rps=256
      transaction_read_profile_hot_burst=256
      transaction_read_profile_archive_burst=256
      transaction_read_profile_hot_delay=0
      transaction_read_profile_archive_delay=0
      ;;
    burst64)
      transaction_read_profile_hot_rate_rps=160
      transaction_read_profile_archive_rate_rps=160
      transaction_read_profile_hot_burst=20
      transaction_read_profile_archive_burst=20
      transaction_read_profile_hot_delay=0
      transaction_read_profile_archive_delay=0
      ;;
    balanced)
      transaction_read_profile_hot_rate_rps=96
      transaction_read_profile_archive_rate_rps=96
      transaction_read_profile_hot_burst=12
      transaction_read_profile_archive_burst=12
      transaction_read_profile_hot_delay=1
      transaction_read_profile_archive_delay=1
      ;;
    fail-fast)
      transaction_read_profile_hot_rate_rps=96
      transaction_read_profile_archive_rate_rps=96
      transaction_read_profile_hot_burst=12
      transaction_read_profile_archive_burst=12
      transaction_read_profile_hot_delay=0
      transaction_read_profile_archive_delay=0
      ;;
    *)
      log "transaction read Nginx budget profile must be burst80, burst64, balanced, or fail-fast: ${transaction_read_budget_profile}"
      exit 1
      ;;
  esac
  transaction_read_hot_rate_rps="${OCI_A1_TRANSACTION_READ_HOT_RATE_RPS:-${transaction_read_profile_hot_rate_rps}}"
  transaction_read_archive_rate_rps="${OCI_A1_TRANSACTION_READ_ARCHIVE_RATE_RPS:-${transaction_read_profile_archive_rate_rps}}"
  transaction_read_hot_burst="${OCI_A1_TRANSACTION_READ_HOT_BURST:-${transaction_read_profile_hot_burst}}"
  transaction_read_archive_burst="${OCI_A1_TRANSACTION_READ_ARCHIVE_BURST:-${transaction_read_profile_archive_burst}}"
  transaction_read_hot_delay="${OCI_A1_TRANSACTION_READ_HOT_DELAY:-${transaction_read_profile_hot_delay}}"
  transaction_read_archive_delay="${OCI_A1_TRANSACTION_READ_ARCHIVE_DELAY:-${transaction_read_profile_archive_delay}}"
  transaction_read_hot_limit_mode="$(render_nginx_limit_req_mode "${transaction_read_hot_delay}")"
  transaction_read_archive_limit_mode="$(render_nginx_limit_req_mode "${transaction_read_archive_delay}")"

  cat <<NGINX
worker_processes auto;

events {
  worker_connections 1024;
}

http {
  limit_req_status 429;
  # upstream latency와 edge limit 결과를 같은 JSON line에 남겨 429 원인을 분리합니다.
  log_format aquila_bank_upstream escape=json
    '{'
      '"time":"\$time_iso8601",'
      '"remote_addr":"\$remote_addr",'
      '"realip_remote_addr":"\$realip_remote_addr",'
      '"request":"\$request",'
      '"status":\$status,'
      '"request_time":\$request_time,'
      '"upstream_status":"\$upstream_status",'
      '"upstream_response_time":"\$upstream_response_time",'
      '"upstream_connect_time":"\$upstream_connect_time",'
      '"upstream_header_time":"\$upstream_header_time",'
      '"limit_req_status":"\$limit_req_status",'
      '"reject_source":"\$sent_http_x_aquila_reject_source",'
      '"reject_reason":"\$sent_http_x_aquila_reject_reason",'
      '"upstream_reject_source":"\$sent_http_x_aquila_429_source",'
      '"upstream_reject_reason":"\$sent_http_x_aquila_reject_reason",'
      '"request_id":"\$request_id",'
      '"k6_run_id":"\$http_x_k6_run_id"'
    '}';
  access_log /var/log/nginx/access.log aquila_bank_upstream;

  # trusted proxy에서만 forwarded client IP를 limiter key의 원천으로 승격합니다.
${real_ip_trusted_proxy_lines}
  real_ip_header ${real_ip_header};
  real_ip_recursive on;

  # 짧은 API 요청만 1차 보호하고, SSE는 exact location과 전용 timeout으로 분리합니다.
  limit_req_zone \$binary_remote_addr zone=aquila_bank_api_per_ip:10m rate=30r/s;
  # 공개 auth 진입점은 token/bcrypt 비용 전에 더 보수적으로 edge 차단합니다.
  limit_req_zone \$binary_remote_addr zone=aquila_bank_auth_per_ip:10m rate=5r/s;
  # active/archive read는 arrival-16 정상 구간을 delay queue 없이 통과시키도록 headroom을 둡니다.
  limit_req_zone \$binary_remote_addr zone=aquila_bank_transaction_hot_per_ip:10m rate=${transaction_read_hot_rate_rps}r/s;
  limit_req_zone \$binary_remote_addr zone=aquila_bank_transaction_archive_per_ip:10m rate=${transaction_read_archive_rate_rps}r/s;
  # transfer write는 정합성 비용이 커서 read-heavy traffic과 별도 fail-fast 예산을 둡니다.
  limit_req_zone \$binary_remote_addr zone=aquila_bank_transfer_per_ip:10m rate=3r/s;

  upstream aquila_bank_backend {
    server ${backend_name}:${BACKEND_PORT};
    keepalive 16;
    keepalive_requests 1000;
    # backend idle close보다 짧게 유지해 stale upstream reuse로 인한 즉시 502를 줄인다.
    keepalive_timeout ${backend_api_keepalive_timeout_seconds}s;
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
    proxy_socket_keepalive on;
    error_page 429 = @aquila_edge_rate_limited;

    location @aquila_edge_rate_limited {
      internal;
      default_type application/json;
      add_header X-Aquila-Reject-Source nginx-edge always;
      add_header X-Aquila-Reject-Reason edge-rate-limit always;
      add_header Retry-After ${edge_retry_after_seconds} always;
      add_header X-RateLimit-Scope nginx-edge always;
      add_header X-RateLimit-Retry-After-Millis ${edge_retry_after_millis} always;
      add_header X-RateLimit-Retry-Jitter-Millis ${edge_retry_jitter_millis} always;
      return 429 '{"error":"rate_limited","source":"nginx-edge","reason":"edge-rate-limit","retryAfterMillis":${edge_retry_after_millis},"retryJitterMillis":${edge_retry_jitter_millis}}';
    }

    location = /api/v1/notifications/stream {
      # SSE 장기 연결은 proxy buffering을 끄고 기존 blue 슬롯을 짧게 drain한다.
      proxy_pass http://aquila_bank_backend;
      proxy_set_header Host ${backend_proxy_host};
      proxy_set_header X-Request-Id \$request_id;
      proxy_set_header X-K6-Run-Id \$http_x_k6_run_id;
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
      proxy_set_header X-Request-Id \$request_id;
      proxy_set_header X-K6-Run-Id \$http_x_k6_run_id;
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

    # 공개 auth는 generic /api/ limit에 섞지 않고 별도 zone으로 먼저 자릅니다.
    location = /api/v1/auth/login {
      proxy_pass http://aquila_bank_backend;
      proxy_set_header Host ${backend_proxy_host};
      proxy_set_header X-Request-Id \$request_id;
      proxy_set_header X-K6-Run-Id \$http_x_k6_run_id;
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_set_header X-Forwarded-Host \$host;
      proxy_set_header X-Forwarded-Port \$server_port;
      proxy_set_header Connection "";
      limit_req zone=aquila_bank_auth_per_ip burst=10 nodelay;
      proxy_read_timeout 30s;
      proxy_send_timeout 30s;
    }

    location = /api/v1/auth/refresh {
      proxy_pass http://aquila_bank_backend;
      proxy_set_header Host ${backend_proxy_host};
      proxy_set_header X-Request-Id \$request_id;
      proxy_set_header X-K6-Run-Id \$http_x_k6_run_id;
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_set_header X-Forwarded-Host \$host;
      proxy_set_header X-Forwarded-Port \$server_port;
      proxy_set_header Connection "";
      limit_req zone=aquila_bank_auth_per_ip burst=10 nodelay;
      proxy_read_timeout 30s;
      proxy_send_timeout 30s;
    }

    location = /api/v1/auth/password-recovery/request {
      proxy_pass http://aquila_bank_backend;
      proxy_set_header Host ${backend_proxy_host};
      proxy_set_header X-Request-Id \$request_id;
      proxy_set_header X-K6-Run-Id \$http_x_k6_run_id;
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_set_header X-Forwarded-Host \$host;
      proxy_set_header X-Forwarded-Port \$server_port;
      proxy_set_header Connection "";
      limit_req zone=aquila_bank_auth_per_ip burst=10 nodelay;
      proxy_read_timeout 30s;
      proxy_send_timeout 30s;
    }

    location = /api/v1/transactions {
      proxy_pass http://aquila_bank_backend;
      proxy_set_header Host ${backend_proxy_host};
      proxy_set_header X-Request-Id \$request_id;
      proxy_set_header X-K6-Run-Id \$http_x_k6_run_id;
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_set_header X-Forwarded-Host \$host;
      proxy_set_header X-Forwarded-Port \$server_port;
      proxy_set_header Connection "";
      # transaction read는 GET 전용이라 stale upstream connection만 짧게 재시도합니다.
      proxy_next_upstream error timeout http_502;
      proxy_next_upstream_tries 2;
      proxy_next_upstream_timeout 2s;
      # 짧은 burst는 작은 delay queue로 흡수하고, queue 초과만 429로 돌려 client backoff와 분리합니다.
      limit_req zone=aquila_bank_transaction_hot_per_ip burst=${transaction_read_hot_burst} ${transaction_read_hot_limit_mode};
      add_header X-Aquila-Edge-Limit-Status \$limit_req_status always;
      proxy_read_timeout 30s;
      proxy_send_timeout 30s;
    }

    location = /api/v1/transactions/archive {
      proxy_pass http://aquila_bank_backend;
      proxy_set_header Host ${backend_proxy_host};
      proxy_set_header X-Request-Id \$request_id;
      proxy_set_header X-K6-Run-Id \$http_x_k6_run_id;
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_set_header X-Forwarded-Host \$host;
      proxy_set_header X-Forwarded-Port \$server_port;
      proxy_set_header Connection "";
      # archive read도 GET 전용이라 connection 경계 502만 bounded retry로 흡수합니다.
      proxy_next_upstream error timeout http_502;
      proxy_next_upstream_tries 2;
      proxy_next_upstream_timeout 2s;
      # archive query도 같은 작은 delay queue로 short-burst edge 429를 먼저 낮춥니다.
      limit_req zone=aquila_bank_transaction_archive_per_ip burst=${transaction_read_archive_burst} ${transaction_read_archive_limit_mode};
      add_header X-Aquila-Edge-Limit-Status \$limit_req_status always;
      proxy_read_timeout 30s;
      proxy_send_timeout 30s;
    }

    location = /api/v1/transfers {
      proxy_pass http://aquila_bank_backend;
      proxy_set_header Host ${backend_proxy_host};
      proxy_set_header X-Request-Id \$request_id;
      proxy_set_header X-K6-Run-Id \$http_x_k6_run_id;
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_set_header X-Forwarded-Host \$host;
      proxy_set_header X-Forwarded-Port \$server_port;
      proxy_set_header Connection "";
      proxy_next_upstream off;
      limit_req zone=aquila_bank_transfer_per_ip burst=6 nodelay;
      proxy_read_timeout 30s;
      proxy_send_timeout 30s;
    }

    location ~ ^/api/v1/transfers/[^/]+/reversal$ {
      proxy_pass http://aquila_bank_backend;
      proxy_set_header Host ${backend_proxy_host};
      proxy_set_header X-Request-Id \$request_id;
      proxy_set_header X-K6-Run-Id \$http_x_k6_run_id;
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_set_header X-Forwarded-Host \$host;
      proxy_set_header X-Forwarded-Port \$server_port;
      proxy_set_header Connection "";
      proxy_next_upstream off;
      limit_req zone=aquila_bank_transfer_per_ip burst=6 nodelay;
      proxy_read_timeout 30s;
      proxy_send_timeout 30s;
    }

    location /api/ {
      proxy_pass http://aquila_bank_backend;
      proxy_set_header Host ${backend_proxy_host};
      proxy_set_header X-Request-Id \$request_id;
      proxy_set_header X-K6-Run-Id \$http_x_k6_run_id;
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_set_header X-Forwarded-Host \$host;
      proxy_set_header X-Forwarded-Port \$server_port;
      proxy_set_header Connection "";
      proxy_next_upstream off;
      limit_req zone=aquila_bank_api_per_ip burst=20 delay=5;
      proxy_read_timeout 30s;
      proxy_send_timeout 30s;
    }

    location / {
      proxy_pass http://aquila_bank_frontend;
      proxy_set_header Host \$host;
      proxy_set_header X-Request-Id \$request_id;
      proxy_set_header X-K6-Run-Id \$http_x_k6_run_id;
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

nginx_container_running() {
  if docker ps --format '{{.Names}}' | grep -qx "${NGINX_CONTAINER}"; then
    return 0
  fi

  return 1
}

ensure_nginx_container() {
  if nginx_container_running; then
    return 0
  fi

  docker rm -f "${NGINX_CONTAINER}" >/dev/null 2>&1 || true
  log "start nginx entrypoint"
  docker run -d \
    --name "${NGINX_CONTAINER}" \
    --restart unless-stopped \
    --network "${NETWORK}" \
    --label com.aquilabank.runtime=oci-a1 \
    --label com.aquilabank.service=nginx \
    -p 80:80 \
    -v "${APP_DIR}/nginx/nginx.conf:/etc/nginx/nginx.conf:ro" \
    nginx:1.27-alpine >/dev/null
}

ensure_nginx_config_visible() {
  local slot="$1"
  local backend_name frontend_name
  backend_name="$(slot_name backend "${slot}")"
  frontend_name="$(slot_name frontend "${slot}")"

  if ! nginx_container_running; then
    return 0
  fi

  # 과거 mv 기반 교체로 남은 stale file bind mount는 컨테이너 내부 config 직접 확인으로만 식별된다.
  if docker exec "${NGINX_CONTAINER}" grep -Fq "server ${backend_name}:${BACKEND_PORT};" /etc/nginx/nginx.conf \
    && docker exec "${NGINX_CONTAINER}" grep -Fq "server ${frontend_name}:${FRONTEND_PORT};" /etc/nginx/nginx.conf; then
    return 0
  fi

  log "stale nginx config bind mount detected; recreate nginx container for active config"
  docker rm -f "${NGINX_CONTAINER}" >/dev/null
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

  if [[ -e "${active_config}" ]]; then
    if [[ -s "${active_config}" ]]; then
      cp "${active_config}" "${backup_config}"
    fi
    # Docker file bind mount는 inode를 따라가므로 기존 active 파일은 제자리에서 갱신한다.
    cat "${next_config}" >"${active_config}"
    rm -f "${next_config}"
  else
    mv "${next_config}" "${active_config}"
  fi
  ensure_nginx_config_visible "${green}"
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
  ensure_kafka_container_for_backend

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
