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
  "upstream aquila_bank_frontend"
  "server 127.0.0.1:3000;"
  "upstream aquila_bank_backend_api"
  "server 127.0.0.1:8080 max_fails=3 fail_timeout=5s;"
  "upstream aquila_bank_backend_sse"
  "least_conn;"
  "server_name bank.example.com;"
  "location ^~ /.well-known/acme-challenge/"
  "return 308 https://\$server_name\$request_uri;"
  "listen 443 ssl http2;"
  "ssl_certificate /etc/letsencrypt/live/bank.example.com/fullchain.pem;"
  "ssl_certificate_key /etc/letsencrypt/live/bank.example.com/privkey.pem;"
  "location = /api/v1/notifications/stream"
  "proxy_pass http://aquila_bank_backend_sse;"
  "proxy_buffering off;"
  "proxy_request_buffering off;"
  "proxy_cache off;"
  "proxy_next_upstream off;"
  "proxy_read_timeout 1900s;"
  "proxy_send_timeout 1900s;"
  "add_header X-Accel-Buffering no always;"
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

ssl_certificate_path="$(sed -n 's/^[[:space:]]*ssl_certificate[[:space:]]\+\([^;]*\);/\1/p' "$config_path" | head -n 1)"
ssl_certificate_key_path="$(sed -n 's/^[[:space:]]*ssl_certificate_key[[:space:]]\+\([^;]*\);/\1/p' "$config_path" | head -n 1)"

if command -v nginx >/dev/null 2>&1 && [[ -n "$ssl_certificate_path" ]] && [[ -n "$ssl_certificate_key_path" ]] && [[ -f "$ssl_certificate_path" ]] && [[ -f "$ssl_certificate_key_path" ]]; then
  nginx -t -c "$PWD/$config_path" >/dev/null
  echo "[nginx-sse-check] nginx -t passed"
else
  echo "[nginx-sse-check] directive smoke check passed"
fi
