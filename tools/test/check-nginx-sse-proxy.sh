#!/usr/bin/env bash
set -euo pipefail

config_path="ops/nginx/nginx.conf"

if [[ ! -f "$config_path" ]]; then
  echo "[nginx-sse-check] missing config: $config_path" >&2
  exit 1
fi

required_patterns=(
  "upstream aquila_bank_frontend"
  "server 127.0.0.1:3000;"
  "upstream aquila_bank_backend"
  "server 127.0.0.1:8080;"
  "location = /api/v1/notifications/stream"
  "proxy_buffering off;"
  "proxy_request_buffering off;"
  "proxy_cache off;"
  "proxy_read_timeout 1900s;"
  "proxy_send_timeout 1900s;"
  "add_header X-Accel-Buffering no always;"
  "location /api/"
  "location ^~ /actuator/health"
  "location / {"
)

for pattern in "${required_patterns[@]}"; do
  if ! rg -F --quiet "$pattern" "$config_path"; then
    echo "[nginx-sse-check] missing directive: $pattern" >&2
    exit 1
  fi
done

if command -v nginx >/dev/null 2>&1; then
  nginx -t -c "$PWD/$config_path" >/dev/null
  echo "[nginx-sse-check] nginx -t passed"
else
  echo "[nginx-sse-check] directive smoke check passed"
fi
