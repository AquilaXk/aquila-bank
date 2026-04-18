# Nginx Reverse Proxy Baseline

`ops/nginx/nginx.conf`는 단일 EC2 인스턴스에서 frontend(`3000`)와 backend(`8080`)를 함께 reverse proxy 하는 기준 파일입니다. 이번 baseline은 HTTPS 종료, exact `server_name`, API rate limit까지 포함합니다.

## 포함 범위

- `/`: frontend upstream
- `/api/`: backend upstream
- `/actuator/health`: backend health check upstream
- `/api/v1/notifications/stream`: SSE 전용 proxy 설정
- `80 -> 443`: 일반 요청 HTTPS redirect
- `443 ssl`: TLS termination
- API 단기 burst 보호용 rate limit

## TLS / `server_name` 기준

- `server_name`은 `_` wildcard 대신 실제 FQDN 하나로 고정합니다. 기본값은 `bank.example.com` placeholder입니다.
- `listen 80`에서는 `/.well-known/acme-challenge/`와 `/actuator/health`만 예외로 두고 나머지는 `308`으로 HTTPS redirect 합니다.
- `listen 443 ssl http2`에서 TLS termination을 수행합니다.
- `ssl_certificate`, `ssl_certificate_key`는 placeholder 경로이므로 운영 적용 전에 실제 인증서 경로로 교체해야 합니다.
- `return 308 https://$server_name$request_uri;`를 써서 요청 `Host` 헤더를 그대로 반사하지 않고 설정한 host 기준으로 redirect 합니다.

## Rate Limit 기준

- `limit_req_zone $binary_remote_addr zone=aquila_bank_api_per_ip:10m rate=30r/s;`
- `/api/`에만 `limit_req zone=aquila_bank_api_per_ip burst=60 nodelay;`를 적용합니다.
- `/api/v1/notifications/stream`은 장기 연결이라 일반 API와 성격이 달라 exact location으로 분리하고 rate limit 대상에서 제외합니다.
- `429`는 Nginx에서 바로 반환해 backend thread/connection 소비를 줄이는 1차 가드로 둡니다.
- 실제 서비스 트래픽 특성에 따라 `rate`와 `burst`는 조정하되, 로그인/토큰 재발급/SSE 재연결 패턴을 같이 확인합니다.

## SSE 기준

- `proxy_buffering off`
- `proxy_request_buffering off`
- `proxy_cache off`
- `gzip off`
- `proxy_read_timeout 1900s`
- `proxy_send_timeout 1900s`

앱 기본값이 `NOTIFICATION_SSE_CONNECTION_TIMEOUT_MS=1800000`, `NOTIFICATION_SSE_HEARTBEAT_INTERVAL_MS=10000` 이라서, proxy timeout은 연결 종료 기준보다 약간 길게 잡아 heartbeat 사이 idle 구간에서 proxy가 먼저 끊지 않게 둡니다.

## 운영 적용 전 확인

- frontend/backend 포트가 기본값과 다르면 upstream `server` 주소를 같이 수정합니다.
- `bank.example.com`, `/etc/letsencrypt/live/...` placeholder는 실제 운영값으로 교체합니다.
- HTTP health probe가 필요 없으면 `listen 80`의 `/actuator/health` 예외도 HTTPS로 통일합니다.
- multi-node load balancer 정책은 이번 baseline 밖입니다.
- backend는 이미 `X-Accel-Buffering: no` 헤더를 내려주므로 Nginx도 같은 방향으로 buffering을 끈 상태를 유지합니다.

## 검증

```bash
bash tools/test/check-nginx-sse-proxy.sh
```

`nginx` binary와 실제 TLS 인증서 파일이 모두 있는 환경이면 위 smoke check가 추가로 `nginx -t`까지 수행합니다. placeholder 인증서 경로만 있는 상태에서는 directive smoke check까지만 수행합니다.
