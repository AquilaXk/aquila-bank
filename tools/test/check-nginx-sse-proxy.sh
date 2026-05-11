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

deploy_path="ops/deploy/oci/bluegreen-deploy.sh"
if [[ ! -f "$deploy_path" ]]; then
  echo "[nginx-sse-check] missing deploy renderer: $deploy_path" >&2
  exit 1
fi

required_patterns=(
  "limit_req_status 429;"
  "server_tokens off;"
  "client_header_timeout 10s;"
  "client_body_timeout 10s;"
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
  "limit_req_zone \$binary_remote_addr zone=aquila_bank_frontend_per_ip:10m rate=10r/s;"
  "limit_req_zone \$binary_remote_addr zone=aquila_bank_transaction_hot_per_ip:10m rate=\${NGINX_TRANSACTION_READ_HOT_RATE_RPS}r/s;"
  "limit_req_zone \$binary_remote_addr zone=aquila_bank_transaction_archive_per_ip:10m rate=\${NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS}r/s;"
  "limit_req_zone \$binary_remote_addr zone=aquila_bank_transfer_per_ip:10m rate=8r/s;"
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
  "add_header X-Content-Type-Options nosniff always;"
  "add_header X-Frame-Options DENY always;"
  "add_header Referrer-Policy no-referrer always;"
  "add_header Permissions-Policy \"geolocation=(), microphone=(), camera=()\" always;"
  "add_header X-Robots-Tag \"noindex, nofollow, noarchive\" always;"
  "add_header Strict-Transport-Security \"max-age=\${NGINX_HSTS_MAX_AGE_SECONDS}; includeSubDomains\" always;"
  "location ~* ^/(?:\\.env(?:\\..*)?|\\.git(?:/|\$)|wp-login\\.php|xmlrpc\\.php|phpmyadmin(?:/|\$)|adminer(?:/|\$)|vendor/phpunit(?:/|\$)|cgi-bin(?:/|\$))"
  "add_header X-Aquila-Reject-Source nginx-bot-guard always;"
  "add_header X-Aquila-Reject-Reason scanner-path always;"
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
  "limit_req zone=aquila_bank_transfer_per_ip burst=16 nodelay;"
  "location /api/"
  "proxy_pass http://aquila_bank_backend_api;"
  "proxy_next_upstream off;"
  "limit_req zone=aquila_bank_api_per_ip burst=20 delay=5;"
  "location ^~ /actuator/health"
  "location / {"
  "limit_req zone=aquila_bank_frontend_per_ip burst=60 delay=20;"
)

for pattern in "${required_patterns[@]}"; do
  if ! contains_pattern "$pattern"; then
    echo "[nginx-sse-check] missing directive: $pattern" >&2
    exit 1
  fi
done

deploy_required_patterns=(
  'NGINX_ENABLE_HTTPS="${NGINX_ENABLE_HTTPS:-auto}"'
  'NGINX_SSL_CERTIFICATE_PATH="${NGINX_SSL_CERTIFICATE_PATH:-/etc/letsencrypt/live/${SERVER_NAME}/fullchain.pem}"'
  "NGINX_ENABLE_HTTPS=true requires NGINX_SERVER_NAME to be a real FQDN"
  "nginx_https_port_flags"
  "443:443"
  "nginx_tls_mount_flags"
  "server_tokens off;"
  "listen 443 ssl http2;"
  'return 308 https://${SERVER_NAME}\$request_uri;'
  'limit_req_zone \$binary_remote_addr zone=aquila_bank_frontend_per_ip:10m rate=10r/s;'
  "add_header X-Aquila-Reject-Source nginx-bot-guard always;"
  "add_header X-Aquila-Reject-Reason scanner-path always;"
  "add_header X-Content-Type-Options nosniff always;"
  "add_header X-Frame-Options DENY always;"
  "add_header Referrer-Policy no-referrer always;"
  "add_header Permissions-Policy \"geolocation=(), microphone=(), camera=()\" always;"
  "add_header X-Robots-Tag \"noindex, nofollow, noarchive\" always;"
  'add_header Strict-Transport-Security \"max-age=${hsts_max_age_seconds}; includeSubDomains\" always;'
  "location ^~ /.well-known/acme-challenge/"
  "limit_req zone=aquila_bank_frontend_per_ip burst=60 delay=20;"
)

for pattern in "${deploy_required_patterns[@]}"; do
  if ! grep -Fq -- "$pattern" "$deploy_path"; then
    echo "[nginx-sse-check] deploy renderer missing directive: $pattern" >&2
    exit 1
  fi
done

if ! grep -Fq 'var\/log\/nginx\/access\.log aquila_bank_upstream' "$runtime_gate_path" ||
  ! grep -Fq 'access_log " access_log_path " aquila_bank_upstream;' "$runtime_gate_path"; then
  echo "[nginx-sse-check] runtime gate must rewrite formatted access_log path for nginx -t" >&2
  exit 1
fi

echo "[nginx-sse-check] template directive smoke check passed"
