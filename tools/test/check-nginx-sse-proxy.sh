#!/usr/bin/env bash
set -euo pipefail

config_path="ops/nginx/nginx.conf"

if [[ ! -f "$config_path" ]]; then
  echo "[nginx-sse-check] missing config: $config_path" >&2
  exit 1
fi

required_patterns=(
  "limit_req_status 429;"
  "limit_req_zone \$binary_remote_addr zone=aquila_bank_api_per_ip:10m rate=30r/s;"
  "limit_req_zone \$binary_remote_addr zone=aquila_bank_auth_per_ip:10m rate=5r/s;"
  "upstream aquila_bank_frontend"
  "\${NGINX_FRONTEND_SERVER_LINES}"
  "upstream aquila_bank_backend_api"
  "\${NGINX_BACKEND_API_SERVER_LINES}"
  "upstream aquila_bank_backend_sse"
  "least_conn;"
  "\${NGINX_BACKEND_SSE_SERVER_LINES}"
  "server_name \${NGINX_SERVER_NAME};"
  "location ^~ /.well-known/acme-challenge/"
  "return 308 https://\$server_name\$request_uri;"
  "listen 443 ssl http2;"
  "ssl_certificate \${NGINX_SSL_CERTIFICATE_PATH};"
  "ssl_certificate_key \${NGINX_SSL_CERTIFICATE_KEY_PATH};"
  "location = /api/v1/notifications/stream"
  "proxy_pass http://aquila_bank_backend_sse;"
  "proxy_buffering off;"
  "proxy_request_buffering off;"
  "proxy_cache off;"
  "proxy_next_upstream off;"
  "proxy_read_timeout 1900s;"
  "proxy_send_timeout 1900s;"
  "add_header X-Accel-Buffering no always;"
  "location = /api/v1/auth/login"
  "location = /api/v1/auth/refresh"
  "location = /api/v1/auth/password-recovery/request"
  "limit_req zone=aquila_bank_auth_per_ip burst=10 nodelay;"
  "location /api/"
  "proxy_pass http://aquila_bank_backend_api;"
  "limit_req zone=aquila_bank_api_per_ip burst=60 nodelay;"
  "location ^~ /actuator/health"
  "location / {"
)

for pattern in "${required_patterns[@]}"; do
  if ! rg -F --quiet "$pattern" "$config_path"; then
    echo "[nginx-sse-check] missing directive: $pattern" >&2
    exit 1
  fi
done

echo "[nginx-sse-check] template directive smoke check passed"
