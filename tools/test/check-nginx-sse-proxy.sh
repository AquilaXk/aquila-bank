#!/usr/bin/env bash
set -euo pipefail

config_path="ops/nginx/nginx.conf"

contains_pattern() {
  local pattern="$1"
  if command -v rg >/dev/null 2>&1; then
    rg -F --quiet -- "$pattern" "$config_path"
    return
  fi
  grep -Fq -- "$pattern" "$config_path"
}

if [[ ! -f "$config_path" ]]; then
  echo "[nginx-sse-check] missing config: $config_path" >&2
  exit 1
fi

runtime_gate_path="tools/test/run-nginx-runtime-template-gate.sh"
if [[ ! -f "$runtime_gate_path" ]]; then
  echo "[nginx-sse-check] missing runtime gate: $runtime_gate_path" >&2
  exit 1
fi

required_patterns=(
  "limit_req_status 429;"
  "log_format aquila_bank_upstream escape=json"
  '"status":$status'
  '"realip_remote_addr":"$realip_remote_addr"'
  '"upstream_status":"$upstream_status"'
  '"request_time":$request_time'
  '"upstream_response_time":"$upstream_response_time"'
  '"upstream_connect_time":"$upstream_connect_time"'
  '"upstream_header_time":"$upstream_header_time"'
  '"limit_req_status":"$limit_req_status"'
  '"reject_source":"$sent_http_x_aquila_reject_source"'
  '"reject_reason":"$sent_http_x_aquila_reject_reason"'
  '"upstream_reject_source":"$sent_http_x_aquila_429_source"'
  '"upstream_reject_reason":"$sent_http_x_aquila_reject_reason"'
  '"request_id":"$request_id"'
  '"k6_run_id":"$http_x_k6_run_id"'
  "access_log /var/log/nginx/access.log aquila_bank_upstream;"
  "\${NGINX_REAL_IP_TRUSTED_PROXY_LINES}"
  "real_ip_header \${NGINX_REAL_IP_HEADER};"
  "real_ip_recursive on;"
  "limit_req_zone \$binary_remote_addr zone=aquila_bank_api_per_ip:10m rate=30r/s;"
  "limit_req_zone \$binary_remote_addr zone=aquila_bank_auth_per_ip:10m rate=5r/s;"
  "limit_req_zone \$binary_remote_addr zone=aquila_bank_transaction_hot_per_ip:10m rate=\${NGINX_TRANSACTION_READ_HOT_RATE_RPS}r/s;"
  "limit_req_zone \$binary_remote_addr zone=aquila_bank_transaction_archive_per_ip:10m rate=\${NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS}r/s;"
  "limit_req_zone \$binary_remote_addr zone=aquila_bank_transfer_per_ip:10m rate=3r/s;"
  "upstream aquila_bank_frontend"
  "\${NGINX_FRONTEND_SERVER_LINES}"
  "upstream aquila_bank_backend_api"
  "\${NGINX_BACKEND_API_SERVER_LINES}"
  "upstream aquila_bank_backend_sse"
  "least_conn;"
  "\${NGINX_BACKEND_SSE_SERVER_LINES}"
  "keepalive_requests 1000;"
  "keepalive_timeout \${NGINX_BACKEND_API_KEEPALIVE_TIMEOUT_SECONDS}s;"
  "server_name \${NGINX_SERVER_NAME};"
  "location ^~ /.well-known/acme-challenge/"
  "return 308 https://\$server_name\$request_uri;"
  "listen 443 ssl http2;"
  "ssl_certificate \${NGINX_SSL_CERTIFICATE_PATH};"
  "ssl_certificate_key \${NGINX_SSL_CERTIFICATE_KEY_PATH};"
  "location = /api/v1/notifications/stream"
  "proxy_pass http://aquila_bank_backend_sse;"
  "proxy_set_header Host \${NGINX_BACKEND_PROXY_HOST};"
  "proxy_set_header X-Request-Id \$request_id;"
  "proxy_set_header X-K6-Run-Id \$http_x_k6_run_id;"
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
  "error_page 429 = @aquila_edge_rate_limited;"
  "location @aquila_edge_rate_limited"
  "add_header X-Aquila-Reject-Source nginx-edge always;"
  "add_header X-Aquila-Reject-Reason edge-rate-limit always;"
  "add_header Retry-After \${NGINX_EDGE_RETRY_AFTER_SECONDS} always;"
  "add_header X-RateLimit-Retry-After-Millis \${NGINX_EDGE_RETRY_AFTER_MILLIS} always;"
  "add_header X-RateLimit-Retry-Jitter-Millis \${NGINX_EDGE_RETRY_JITTER_MILLIS} always;"
  "add_header X-Aquila-Edge-Limit-Status \$limit_req_status always;"
  '"source":"nginx-edge"'
  "location = /api/v1/transactions"
  "location = /api/v1/transactions/archive"
  "limit_req zone=aquila_bank_transaction_hot_per_ip burst=\${NGINX_TRANSACTION_READ_HOT_BURST} \${NGINX_TRANSACTION_READ_HOT_LIMIT_MODE};"
  "limit_req zone=aquila_bank_transaction_archive_per_ip burst=\${NGINX_TRANSACTION_READ_ARCHIVE_BURST} \${NGINX_TRANSACTION_READ_ARCHIVE_LIMIT_MODE};"
  "proxy_next_upstream error timeout http_502;"
  "proxy_next_upstream_tries 2;"
  "proxy_next_upstream_timeout 2s;"
  "location = /api/v1/transfers"
  "location ~ ^/api/v1/transfers/[^/]+/reversal$"
  "limit_req zone=aquila_bank_transfer_per_ip burst=6 nodelay;"
  "location /api/"
  "proxy_pass http://aquila_bank_backend_api;"
  "proxy_next_upstream off;"
  "limit_req zone=aquila_bank_api_per_ip burst=20 delay=5;"
  "location ^~ /actuator/health"
  "location / {"
)

for pattern in "${required_patterns[@]}"; do
  if ! contains_pattern "$pattern"; then
    echo "[nginx-sse-check] missing directive: $pattern" >&2
    exit 1
  fi
done

if ! grep -Fq 'var\/log\/nginx\/access\.log aquila_bank_upstream' "$runtime_gate_path" ||
  ! grep -Fq 'access_log " access_log_path " aquila_bank_upstream;' "$runtime_gate_path"; then
  echo "[nginx-sse-check] runtime gate must rewrite formatted access_log path for nginx -t" >&2
  exit 1
fi

echo "[nginx-sse-check] template directive smoke check passed"
