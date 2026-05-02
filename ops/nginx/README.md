# Nginx Reverse Proxy Baseline

`ops/nginx/nginx.conf`는 frontend(`3000`)와 backend(`8080`)를 reverse proxy 하는 template 기준 파일입니다. runtime 값은 `ops/nginx/runtime.env.example`과 `tools/ops/render-nginx-runtime-config.sh`로 렌더링하고, 최종 rendered config는 Git 추적 대상에 두지 않습니다.

## 포함 범위

- `/`: frontend upstream
- `/api/`: backend upstream
- `/actuator/health`: backend health check upstream
- `/api/v1/notifications/stream`: SSE 전용 proxy 설정
- `80 -> 443`: 일반 요청 HTTPS redirect
- `443 ssl`: TLS termination
- API 단기 burst 보호용 rate limit
- transaction-read 전용 edge rate limit
- API/SSE upstream 분리
- multi-node backend pool placeholder

## Runtime Render 기준

- runtime env 예시는 `ops/nginx/runtime.env.example`에 둡니다.
- 필수 env:
  - `NGINX_SERVER_NAME`
  - `NGINX_SSL_CERTIFICATE_PATH`
  - `NGINX_SSL_CERTIFICATE_KEY_PATH`
  - `NGINX_FRONTEND_SERVER`
  - `NGINX_BACKEND_API_SERVERS`
- 선택 env:
  - `NGINX_EDGE_RETRY_AFTER_SECONDS` 기본값 `1`
  - `NGINX_EDGE_RETRY_AFTER_MILLIS` 기본값 `150`
  - `NGINX_EDGE_RETRY_JITTER_MILLIS` 기본값 `100`
  - `NGINX_REAL_IP_HEADER` 기본값 `X-Forwarded-For`, 허용값 `X-Forwarded-For` 또는 `X-Real-IP`
  - `NGINX_REAL_IP_TRUSTED_PROXIES` 기본값 `10.60.0.0/16`, comma-separated trusted LB/CDN CIDR, `none`이면 TCP peer address
  - `NGINX_TRANSACTION_READ_BUDGET_PROFILE` 기본값 `burst64`, 허용값 `burst64|balanced|fail-fast`
  - `NGINX_TRANSACTION_READ_HOT_RATE_RPS` 기본값 `128`
  - `NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS` 기본값 `128`
  - `NGINX_TRANSACTION_READ_HOT_BURST` 기본값 `16`
  - `NGINX_TRANSACTION_READ_ARCHIVE_BURST` 기본값 `16`
  - `NGINX_TRANSACTION_READ_HOT_DELAY` 기본값 `0`, `0`이면 `nodelay`
  - `NGINX_TRANSACTION_READ_ARCHIVE_DELAY` 기본값 `0`, `0`이면 `nodelay`
- `NGINX_BACKEND_SSE_SERVERS`를 비우면 API upstream과 같은 backend pool을 재사용합니다.
- render 명령:

```bash
bash tools/ops/render-nginx-runtime-config.sh /tmp/aquila-bank-nginx.conf ops/nginx/runtime.env.example
```

- render script는 값 누락 시 fail-fast 하고, upstream server list를 multi-node CSV에서 `server ... max_fails=3 fail_timeout=5s;` block으로 풀어냅니다.

## TLS / `server_name` 기준

- `server_name`은 `_` wildcard 대신 실제 FQDN 하나로 고정합니다. template에서는 `${NGINX_SERVER_NAME}` placeholder를 사용합니다.
- `listen 80`에서는 `/.well-known/acme-challenge/`와 `/actuator/health`만 예외로 두고 나머지는 `308`으로 HTTPS redirect 합니다.
- `listen 443 ssl http2`에서 TLS termination을 수행합니다.
- `ssl_certificate`, `ssl_certificate_key`는 `${NGINX_SSL_CERTIFICATE_PATH}`, `${NGINX_SSL_CERTIFICATE_KEY_PATH}`를 통해 runtime에서 채웁니다.
- `return 308 https://$server_name$request_uri;`를 써서 요청 `Host` 헤더를 그대로 반사하지 않고 설정한 host 기준으로 redirect 합니다.

## Rate Limit 기준

- `NGINX_REAL_IP_TRUSTED_PROXIES`가 설정된 proxy/LB CIDR에서 온 요청만 `NGINX_REAL_IP_HEADER` 값을 real client IP로 승격합니다.
- OCI paid A1 기본값은 VCN CIDR `10.60.0.0/16`입니다. LB/CDN subnet을 더 좁게 알면 해당 CIDR로 줄이고, TCP peer 기준으로 되돌릴 때만 `none`을 사용합니다.
- `limit_req_zone $binary_remote_addr zone=aquila_bank_api_per_ip:10m rate=30r/s;`
- `limit_req_zone $binary_remote_addr zone=aquila_bank_auth_per_ip:10m rate=5r/s;`
- `limit_req_zone $binary_remote_addr zone=aquila_bank_transaction_hot_per_ip:10m rate=128r/s;`
- `limit_req_zone $binary_remote_addr zone=aquila_bank_transaction_archive_per_ip:10m rate=128r/s;`
- `limit_req_zone $binary_remote_addr zone=aquila_bank_transfer_per_ip:10m rate=3r/s;`
- `location = /api/v1/auth/login`, `location = /api/v1/auth/refresh`, `location = /api/v1/auth/password-recovery/request`에 `limit_req zone=aquila_bank_auth_per_ip burst=10 nodelay;`를 적용합니다.
- `location = /api/v1/transactions`에는 `limit_req zone=aquila_bank_transaction_hot_per_ip burst=16 nodelay;`를 적용합니다.
- `location = /api/v1/transactions/archive`에는 `limit_req zone=aquila_bank_transaction_archive_per_ip burst=16 nodelay;`를 적용합니다.
- `location = /api/v1/transfers`, `location ~ ^/api/v1/transfers/[^/]+/reversal$`에는 `limit_req zone=aquila_bank_transfer_per_ip burst=6 nodelay;`를 적용합니다.
- exact/regex location은 generic `/api/`보다 먼저 매칭되므로 zone을 중첩 적용하지 않습니다.
- `/api/`에는 `limit_req zone=aquila_bank_api_per_ip burst=20 delay=5;`를 유지합니다.
- `/api/v1/notifications/stream`은 장기 연결이라 일반 API와 성격이 달라 exact location으로 분리하고 rate limit 대상에서 제외합니다.
- `429`는 Nginx에서 JSON body와 `X-Aquila-Reject-Source: nginx-edge`, `Retry-After`, `X-RateLimit-Retry-After-Millis`, `X-RateLimit-Retry-Jitter-Millis`를 내려 k6/client backoff가 edge rejection을 구분하게 합니다. OCI A1 기본값은 `150ms + jitter 100ms`로 retry 동기화를 짧게 분산합니다.
- 정상 client/SDK는 `X-RateLimit-Retry-After-Millis`와 jitter를 반영하고, sustained read에서는 k6 `preemptive pacing`과 같은 요청 전 token pacing으로 edge reject 동기화를 피합니다.
- backend에는 login/password recovery throttling이 이미 있으므로, Nginx auth zone은 edge 1차 차단으로 보고 backend는 계정/IP 단위 2차 가드로 둡니다.
- transaction-read `burst64` profile은 delay queue 의존을 줄이기 위해 `128r/s`, `burst=16`, `nodelay`를 기본값으로 둡니다. 이전 queue 기반 기준은 `balanced` profile로 되돌릴 수 있습니다.
- `fail-fast` profile은 `96r/s`, `burst=12`, `nodelay`로 delayed ratio ceiling 검증이나 latency 우선 rollback에 사용합니다.
- 실제 서비스 트래픽 특성에 따라 `rate`와 `burst`는 조정하되, 로그인/토큰 재발급/SSE 재연결 패턴과 shared IP 영향을 같이 확인합니다.

## Multi-Node Load Balancer 기준

- backend upstream은 `aquila_bank_backend_api`, `aquila_bank_backend_sse` 두 개로 분리합니다.
- multi-node에서는 두 upstream의 `server` 목록을 같은 backend pool로 유지합니다.
- API upstream은 기본 round-robin으로 짧은 요청을 처리하고, SSE upstream은 `least_conn`으로 장기 연결을 분산합니다.
- 각 backend server는 `max_fails=3 fail_timeout=5s` passive failure 기준을 둡니다.
- Nginx OSS 기본선에서는 active health check 대신 passive failure detection + `/actuator/health` probe를 함께 사용합니다.
- 배포/드레인 시에는 대상 인스턴스를 두 upstream에서 먼저 제거하고 `nginx -s reload` 후 SSE reconnect 여유를 둔 다음 종료합니다.

## SSE 라우팅 기준

- `/api/v1/notifications/stream`은 exact location과 `aquila_bank_backend_sse` 전용 upstream으로 분리합니다.
- sticky session은 기본값으로 강제하지 않습니다. 한 번 붙은 SSE 연결은 같은 app instance에 유지되고, 재연결은 어느 노드로 가도 `Last-Event-ID` replay로 복구합니다.
- backend는 같은 인스턴스 안에서는 local publish, 다른 인스턴스에는 PostgreSQL `LISTEN/NOTIFY` fan-out을 사용하므로 LB는 backend pool 전체에 연결을 분산해도 됩니다.
- `proxy_next_upstream off`로 SSE를 투명 재시도하지 않고, 실패는 client reconnect + pull API 재동기화 계약으로 넘깁니다.
- SSE upstream server 목록은 API upstream과 다르게 유지하지 말고 동일 backend pool을 써야 fan-out 대상과 라우팅 해석이 단순합니다.

## SSE 기준

- `proxy_buffering off`
- `proxy_request_buffering off`
- `proxy_cache off`
- `gzip off`
- `proxy_read_timeout 1900s`
- `proxy_send_timeout 1900s`

앱 기본값이 `NOTIFICATION_SSE_CONNECTION_TIMEOUT_MS=1800000`, `NOTIFICATION_SSE_HEARTBEAT_INTERVAL_MS=10000` 이라서, proxy timeout은 연결 종료 기준보다 약간 길게 잡아 heartbeat 사이 idle 구간에서 proxy가 먼저 끊지 않게 둡니다.

## 운영 적용 전 확인

- frontend/backend 포트가 기본값과 다르면 runtime env의 upstream 값을 같이 수정합니다.
- `bank.example.com`, `/etc/letsencrypt/live/...` 같은 예시 값은 `runtime.env`에서만 관리하고, rendered config는 artifact로만 사용합니다.
- HTTP health probe가 필요 없으면 `listen 80`의 `/actuator/health` 예외도 HTTPS로 통일합니다.
- multi-node로 확장할 때는 `aquila_bank_backend_api`와 `aquila_bank_backend_sse` 두 upstream에 같은 backend node 집합을 반영합니다.
- 인스턴스 drain 시에는 대상 node를 upstream에서 제거한 뒤 reload 하고, client reconnect/pull 재동기화가 끝날 시간을 둡니다.
- backend는 이미 `X-Accel-Buffering: no` 헤더를 내려주므로 Nginx도 같은 방향으로 buffering을 끈 상태를 유지합니다.
- 운영 smoke는 `tools/test/run-sse-multinode-drain-smoke.sh`로 같은 순서를 반복 검증합니다.

## 검증

```bash
bash tools/test/check-nginx-sse-proxy.sh
bash tools/test/run-nginx-runtime-template-gate.sh
bash tools/test/check-transaction-read-short-burst-smoothing-matrix.sh
tools/test/run-sse-multinode-drain-smoke.sh --print-plan
tools/test/run-sse-multinode-drain-smoke.sh
```

- `check-nginx-sse-proxy.sh`는 template directive drift만 확인합니다.
- `run-nginx-runtime-template-gate.sh`는 env render 후 unresolved placeholder를 막고, `nginx` binary가 있으면 `nginx -t`까지 수행합니다.
- `check-transaction-read-short-burst-smoothing-matrix.sh`는 `nodelay`와 shallow delay queue 후보를 429/p95/p99/Retry-After/5xx 기준으로 비교합니다.
- strict gate는 PR workflow `Nginx Runtime Gate`에서 `nginx`와 `openssl`을 설치한 뒤 같은 script를 `NGINX_RUNTIME_GATE_STRICT=true`로 실행합니다.
