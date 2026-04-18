# Nginx Reverse Proxy Baseline

`ops/nginx/nginx.conf`는 단일 EC2 인스턴스에서 frontend(`3000`)와 backend(`8080`)를 함께 reverse proxy 하는 기준 파일입니다.

## 포함 범위

- `/`: frontend upstream
- `/api/`: backend upstream
- `/actuator/health`: backend health check upstream
- `/api/v1/notifications/stream`: SSE 전용 proxy 설정

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
- TLS termination, `server_name`, rate limit, multi-node load balancer 정책은 이번 baseline 밖입니다.
- backend는 이미 `X-Accel-Buffering: no` 헤더를 내려주므로 Nginx도 같은 방향으로 buffering을 끈 상태를 유지합니다.

## 검증

```bash
bash tools/test/check-nginx-sse-proxy.sh
```

`nginx` binary가 설치된 환경이면 위 smoke check가 추가로 `nginx -t`까지 수행합니다.
