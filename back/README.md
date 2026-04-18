# Aquila Bank Backend

Spring Boot 4 기반 백엔드 애플리케이션입니다.

## Goal

- 실시간 알림과 1억 건 규모의 거래 조회를 `t3.micro` 환경에서도 원활하게 처리하는 구조를 목표로 합니다.
- 로컬/배포 환경 모두 `PostgreSQL 18`을 표준 DB 버전으로 사용합니다.

## Package Structure

```text
com.aquilabank
├── domain
├── global
└── standard
    └── util
```

- `domain`: 도메인 엔티티, 유스케이스, 포트, 도메인 서비스
- `global`: 설정, 보안, 웹 어댑터, DB 어댑터, 예외 처리
- `standard/util`: 공통 규약과 최소 유틸리티

## Architecture

- 헥사고날 아키텍처 준수를 명시적 원칙으로 삼습니다.
- domain은 Spring MVC, JPA, 외부 API SDK에 직접 의존하지 않습니다.
- inbound/outbound adapter, config, filter, interceptor, repository 구현체는 `global`에 둡니다.
- 공통 helper는 `standard/util`에 두되, 도메인 규칙이나 유스케이스 판단은 넣지 않습니다.

## Stack

- Java 21
- Gradle Kotlin DSL
- Spring Boot 4
- PostgreSQL 18
- Flyway
- Spring JDBC / HikariCP
- Spring Boot Actuator

## Transaction Query Baseline

거래 조회 성능 기준선은 `GET /api/v1/transactions` 의 query shape, index 사용 여부, timeout 가드, p95 목표를 함께 보도록 고정합니다.

- baseline fixture:
  - hot account `8만 건`
  - noise account `6계좌 x 4천 건`
  - 30일 조회 창
  - fixture 적재 뒤 `ANALYZE` 수행
- 대표 조회 3종:
  - 첫 page: `accountId + from/to + limit`
  - 후속 cursor page: `accountId + from/to + cursor + limit`
  - 상태 필터 page: `accountId + from/to + status + cursor/limit`
- baseline p95 목표:
  - 첫 page `<= 120ms`
  - 후속 cursor page `<= 150ms`
  - 상태 필터 page `<= 150ms`
- timeout 보호 순서:
  - datasource `statement_timeout=3000ms`
  - Spring JDBC `query-timeout=3s`
  - MVC async `request-timeout=5000ms`
- 운영 해석 기준:
  - p95 목표는 baseline fixture 기준 회귀 감지선입니다.
  - 1억 건 전체를 로컬에 적재하는 대신, planner가 계좌/기간/status/cursor 조건으로 bounded index range scan을 유지하는지 먼저 확인합니다.
  - `Seq Scan`, 불필요한 `Sort`, timeout 근접 실행 시간이 보이면 index 또는 query shape를 다시 검토합니다.

재현 명령:

```bash
tools/test/with-resource-lock.sh back-transaction-baseline \
  tools/test/run-transaction-query-baseline.sh
```

## Transaction Read Model Partition / Archive Fit

partition/archive 는 `GET /api/v1/transactions` read path를 실제로 줄여줄 때만 고려합니다.

- committed 검증 fixture:
  - recent window `8만 건`
  - history window `12개월 x 1.2만 건`
  - 같은 hot account 에 누적
- committed 검증 query:
  - 최근 31일 첫 page
  - 1년 전 31일 첫 page
  - 1년 전 31일 `status` 필터 page
- 현재 결론:
  - 현재 API는 `accountId + from/to(최대 31일) + keyset cursor`로 강하게 bounded 되어 있습니다.
  - committed partition-fit 테스트에서 최근 창과 과거 창 모두 `idx_transaction_read_model_account_cursor` 또는 `idx_transaction_read_model_account_status_cursor`를 유지하고 `Seq Scan`/`Sort`가 나오지 않습니다.
  - 따라서 현재 계약 기준에서는 partition 이 read latency의 첫 레버가 아닙니다.
  - archive 역시 현재 read latency 최적화 목적만으로는 근거가 부족하고, 보존/백업/autovacuum 비용이 커질 때 재검토하는 편이 맞습니다.
- 재검토 트리거:
  - 31일 초과 조회 또는 account scope 없는 조회가 필요해질 때
  - 최근 31일 hot account 조회가 baseline p95/timeout 기준을 깨기 시작할 때
  - `transaction_read_model`의 historical row 때문에 autovacuum lag, index bloat, backup 시간이 운영 병목이 될 때
  - cold history와 hot path의 보존/SLA가 달라 separate archive tier가 필요해질 때

재현 명령:

```bash
tools/test/with-resource-lock.sh back-transaction-partition-fit \
  tools/test/run-transaction-read-model-partition-fit.sh
```

## Run

```bash
./gradlew bootRun
```

## Local Infra

```bash
cp ../.env.example ../.env
cd ..
docker compose up -d postgres kafka
```

PostgreSQL 18로 올린 뒤 기존 local named volume 때문에 `aquila-bank-postgres`가 `Restarting (1)` 상태면 Postgres volume을 한 번 재생성해야 합니다.

```bash
docker compose down
docker volume rm aquila-bank_aquila-bank-postgres-data
docker compose up -d postgres kafka
```

루트 [compose.yml](/Users/aquila/Custom/GitProjects/aquila-bank/compose.yml)은 `postgres:18`과 단일 노드 Kafka broker를 함께 올립니다.

- PostgreSQL 기본 포트: `localhost:5432`
- Kafka 기본 포트: `localhost:9092`
- Kafka topic은 broker 기본 auto-create를 사용하되, app 설정은 `TransferBooked`/`TransferReversed`를 분리해 consumer 충돌을 막습니다.
- 이 경로는 로컬 개발 전용입니다.

## Deployment Baseline

- 백엔드 런타임: `EC2`
- 데이터베이스: `RDS PostgreSQL 18`
- EC2 reverse proxy baseline: [ops/nginx/nginx.conf](/Users/aquila/Custom/GitProjects/aquila-bank/ops/nginx/nginx.conf)
- prod profile은 `DB_URL`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` 환경변수를 받아 EC2에서 외부 PostgreSQL에 연결합니다.
- `compose.yml`은 배포 인프라를 대체하지 않으며, 운영 DB는 local Docker volume이 아니라 관리형 PostgreSQL 기준으로 봅니다.

백엔드는 [back/.env.example](/Users/aquila/Custom/GitProjects/aquila-bank/back/.env.example)를 복사한 뒤 실행합니다.

```bash
cp back/.env.example back/.env
set -a
source back/.env
export OUTBOX_KAFKA_ENABLED=true
export NOTIFICATION_INBOX_CONSUMER_ENABLED=true
set +a
./back/gradlew -p back bootRun
```

- 애플리케이션 시작 시 `back/src/main/resources/db/migration`의 Flyway 마이그레이션이 자동 적용됩니다.
- 기본값은 `t3.micro`를 전제로 작은 커넥션 풀과 짧은 DB 타임아웃을 사용합니다.
- 운영 기본 인증 방식은 bearer JWT 입니다.
- `dev`/`test` 프로필에서는 필요 시 `X-Account-Id` 헤더 fallback을 사용할 수 있습니다.
- `dev` 프로필은 `OUTBOX_KAFKA_ENABLED=true`, `NOTIFICATION_INBOX_CONSUMER_ENABLED=true`만 주면 `localhost:9092`와 기본 topic 이름을 자동 사용합니다.
- Kafka 포트를 바꾸면 `OUTBOX_KAFKA_BOOTSTRAP_SERVERS`, `NOTIFICATION_INBOX_CONSUMER_BOOTSTRAP_SERVERS`를 같은 값으로 같이 넘깁니다.

## Prometheus Metrics

- export endpoint:
  - `GET /actuator/prometheus`
- 보안 기준:
  - application security는 `/actuator/prometheus`를 permitAll 로 열어 Prometheus scrape를 단순화합니다.
  - 운영에서는 security group, private subnet, Nginx allowlist 같은 네트워크 경계로 외부 공개를 막는 것을 기본값으로 둡니다.
- custom metric:
  - `aquila_outbox_dispatch_lag_seconds`
  - `aquila_outbox_failed_count`
  - `aquila_outbox_failed_producer_timeout_count`
  - `aquila_outbox_sending_stale_count`
  - `aquila_notification_consumer_lag_count`
  - `aquila_notification_consumer_dlq_count`
  - `aquila_notification_sse_sessions{principal_type="account|user|total"}`
  - `aquila_transaction_query_latency_seconds`
- 활성화 조건:
  - outbox metric은 기본 wiring만 있으면 항상 export 됩니다.
  - notification consumer lag/DLQ metric은 `NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED=true` 와 DLQ topic 설정이 있어야 export 됩니다.
  - transaction latency timer는 `GET /api/v1/transactions` query path가 한 번이라도 호출되면 `query_shape` tag 기준으로 누적됩니다.
- transaction `query_shape` 기준:
  - `first_page`
  - `cursor`
  - `status_first`, `status_cursor`
  - `direction_first`, `direction_cursor`
  - `amount_first`, `amount_cursor`
  - `mixed_first`, `mixed_cursor`
  - `reference_exact`
- 운영 메모:
  - outbox/notification gauge는 scrape 한 번에 같은 summary를 여러 번 다시 조회하지 않게 `5초` cache 안에서 재사용합니다.
  - SSE session metric은 현재 app instance 메모리의 active session 수만 보여주므로 multi-instance 전체 합계는 Prometheus 쿼리에서 합산합니다.

## Outbox Retention Cleanup

- outbox retention cleanup은 `publish_status = 'PUBLISHED'` 이고 `published_at` 이 retention cutoff 밖인 row만 정리합니다.
- `PENDING`, `FAILED`, `SENDING` row는 dispatch 복구와 ops 확인 대상이라 cleanup에서 제외합니다.
- cleanup batch는 `published_at ASC, id ASC` 순서의 작은 batch delete만 수행해 `t3.micro`에서 lock/vacuum 충격을 낮춥니다.
- 기본 설정은 `OUTBOX_CLEANUP_ENABLED=true`, `OUTBOX_CLEANUP_RETENTION_DAYS=30`, `OUTBOX_CLEANUP_BATCH_SIZE=500`, `OUTBOX_CLEANUP_FIXED_DELAY_MS=300000` 입니다.

### Local Notification E2E

실제 broker를 통과하는 알림 검증은 transfer write path와 outbox dispatch를 같이 봐야 합니다.

```bash
tools/test/with-resource-lock.sh back-gradle-check \
  ./back/gradlew -p back test --tests '*NotificationKafkaE2eIntegrationTest'
```

- 위 테스트는 Testcontainers PostgreSQL + Kafka를 띄워 `transfer API -> outbox -> Kafka -> notification_inbox` 전체 경로를 검증합니다.
- 로컬 앱을 직접 띄운 뒤 수동 확인이 필요하면 transfer 호출 후 notification API 또는 DB `notification_inbox` row를 확인합니다.

## Notification Read State

- JWT user 경로의 읽음 상태는 `notification_user_read_state`에 user별로 저장됩니다.
- `notification_inbox`는 account-scoped read model 본체를 유지하고, `read_at`은 account principal/internal 경로 의미로 분리됩니다.
- 기존 shared `read_at` 값은 user read state로 자동 backfill 하지 않으므로, 배포 이전 알림은 JWT user 기준에서 다시 unread로 보일 수 있습니다.
- notification inbox retention cleanup은 `notification_inbox.created_at` 기준으로만 동작해 account/user 경로의 read 의미를 따로 해석하지 않습니다.
- 오래된 inbox row를 지울 때 연결된 `notification_user_read_state`도 FK cascade로 함께 정리해 read state orphan과 unread 회귀를 막습니다.
- 기본 설정은 `NOTIFICATION_INBOX_CLEANUP_ENABLED=true`, `NOTIFICATION_INBOX_CLEANUP_RETENTION_DAYS=90`, `NOTIFICATION_INBOX_CLEANUP_BATCH_SIZE=500`, `NOTIFICATION_INBOX_CLEANUP_FIXED_DELAY_MS=300000` 입니다.

## Notification SSE Stream

- endpoint:
  - `GET /api/v1/notifications/stream`
- 인증 기준:
  - 운영 기본선은 bearer JWT user
  - `dev`/`test` 에서는 `X-Account-Id` bootstrap header도 같은 endpoint로 사용 가능
- SSE event 이름:
  - `connected`: stream handshake 완료
  - `notification`: 새 inbox row payload
  - `heartbeat`: idle 연결 유지
- 전달 계약:
  - SSE push는 `notification_inbox` insert 성공 이후에만 발행됩니다.
  - 같은 인스턴스 안에서는 local event로 즉시 fan-out 하고, 다른 인스턴스에는 PostgreSQL `LISTEN/NOTIFY` signal로 fan-out 합니다.
  - duplicate Kafka consume로 insert가 `ON CONFLICT DO NOTHING` 이면 SSE도 추가 발행하지 않습니다.
  - user stream fan-out 대상은 `ACTIVE membership + ACTIVE user` 조건으로만 계산합니다.
  - `Last-Event-ID` header가 있으면 `notification_inbox.id > header` 범위를 `id ASC` 순서로 replay 합니다.
  - replay 중 들어온 live 알림은 같은 session lock 안에서 이어 보내 duplicate/out-of-order push를 막습니다.
  - 총 active session 수가 `NOTIFICATION_SSE_MAX_TOTAL_SESSIONS`를 넘으면 새 구독은 `503 Service Unavailable`으로 즉시 거절합니다.
  - replay 중 live backlog 가 `NOTIFICATION_SSE_MAX_PENDING_EVENTS_PER_SESSION`를 넘으면 해당 session만 끊고 client reconnect + pull API 재동기화로 넘깁니다.
  - replay 는 `NOTIFICATION_SSE_REPLAY_LIMIT` 기본값 100건까지만 수행하고, 그보다 큰 reconnect gap 이나 PostgreSQL LISTEN 연결 재수립 사이의 누락은 기존 `GET /api/v1/notifications` pull API로 재동기화합니다.
  - ordering 보장 범위는 같은 fan-out signal 안의 `notification_inbox.id ASC` 처리 순서와, 같은 reconnect session 안의 replay `id ASC` 순서, 그리고 단일 app instance 안의 live publish 순서까지입니다.
- 기본 설정:
  - `NOTIFICATION_SSE_CONNECTION_TIMEOUT_MS=1800000`
  - `NOTIFICATION_SSE_HEARTBEAT_INTERVAL_MS=10000`
  - `NOTIFICATION_SSE_RECONNECT_DELAY_MS=3000`
  - `NOTIFICATION_SSE_REPLAY_LIMIT=100`
  - `NOTIFICATION_SSE_MAX_TOTAL_SESSIONS=64`
  - `NOTIFICATION_SSE_MAX_PENDING_EVENTS_PER_SESSION=32`
  - `NOTIFICATION_SSE_FANOUT_CHANNEL=notification_sse_fanout`
- 운영 메트릭:
  - `aquila_notification_sse_sessions{principal_type="account|user|total"}`
  - `aquila_notification_sse_subscription_rejected_count{reason="session_limit"}`
  - `aquila_notification_sse_session_dropped_count{reason="pending_overflow"}`

### Nginx Reverse Proxy Baseline

- 기준 파일: [ops/nginx/nginx.conf](/Users/aquila/Custom/GitProjects/aquila-bank/ops/nginx/nginx.conf)
- upstream 기본값:
  - frontend `127.0.0.1:3000`
  - backend `127.0.0.1:8080`
- SSE location 운영 기준:
  - `proxy_buffering off`
  - `proxy_request_buffering off`
  - `proxy_cache off`
  - `gzip off`
  - `proxy_read_timeout 1900s`
  - `proxy_send_timeout 1900s`
- 일반 proxy 기준:
  - `/api/` 는 `30s`
  - `/actuator/health` 는 `5s`
  - `/` frontend 는 `60s`
- 검증 명령:

```bash
bash tools/test/check-nginx-sse-proxy.sh
```

- backend가 이미 `X-Accel-Buffering: no` 헤더를 응답하므로 Nginx도 buffering off 상태를 같이 유지합니다.
- `NOTIFICATION_SSE_CONNECTION_TIMEOUT_MS` 또는 upstream 포트를 바꾸면 Nginx timeout/upstream도 같이 맞춥니다.

## Transfer Reversal

기존 BOOKED 송금은 직접 수정하지 않고 reversal transaction을 추가해 취소/정정합니다.

- 공개 endpoint:
  - `POST /api/v1/transfers`
  - `POST /api/v1/transfers/{transactionReference}/reversal`
- reversal 요청 필드:
  - `sourceAccountId`
  - `reversalReason`: `CANCEL` | `CORRECTION`
  - `summary`
- reversal 응답 필드:
  - `originalTransactionReference`
  - `reversalTransactionReference`
  - `sourceAccountId`
  - `targetAccountId`
  - `amountMinor`
  - `currencyCode`
  - `availableBalanceAfterMinor`
  - `bookedAt`
  - `status`
- write 기준:
  - 원본 transfer와 reversal 관계는 `transfer_reversal` 테이블로 관리
  - 원본 ledger row는 수정하지 않고 반대 방향 ledger entry 2건을 새 transaction reference로 추가
  - 원본 `transaction_read_model` 상태는 `REVERSED` 로 전이하고 reversal read model row 2건을 추가
  - outbox는 `TransferReversed` event를 별도 적재
- 충돌 기준:
  - 같은 원본 transfer를 다시 reversal 하면 `409 transfer is already reversed`
  - reversal 시 target 계좌 잔액이 부족하면 `409 reversal target balance is not enough`
- 운영 주의:
  - 이번 범위는 full reversal만 지원하고 partial reversal은 제외
  - notification inbox consumer는 아직 `TransferReversed` fan-out을 처리하지 않음

## Public Login Protection

공개 login 경로 `/api/v1/auth/login`에는 brute-force 1차 방어 기준이 기본 적용됩니다.

- 기본값:
  - `SECURITY_LOGIN_PROTECTION_MAX_FAILURES=5`
  - `SECURITY_LOGIN_PROTECTION_LOCK_SECONDS=900`
  - `SECURITY_LOGIN_PROTECTION_RESET_WINDOW_SECONDS=900`
- 동작 기준:
  - 같은 `loginId`에서 연속 `5회` 실패하면 `15분` 임시 잠금
  - 마지막 실패 후 `15분`이 지나면 실패 카운트는 다시 `1`부터 계산
  - 성공 login 시 `failed_login_count`, `last_login_failed_at`, `login_locked_until`은 reset
  - 외부 응답은 존재 여부/잠금 여부를 드러내지 않도록 항상 `401 login failed` 유지
- 상태 우선순위:
  - 수동 운영 상태 `user_status=LOCKED|DISABLED`가 임시 잠금보다 우선
  - 임시 brute-force 잠금은 `login_locked_until`로만 관리하고 `user_status`는 직접 바꾸지 않음

### login 실패 감사 로그

login 실패/잠금은 structured log 한 줄로 남습니다.

| field | 의미 | 운영 사용 기준 |
| --- | --- | --- |
| `requestId` | 호출 상관키 | login incident drill-down |
| `loginIdHash` | raw `loginId` 대신 남기는 SHA-256 hash | 개인정보 노출 없이 동일 loginId 반복 추적 |
| `userId` | 존재하는 사용자면 user id, 아니면 `-` | 계정 존재/대상 추적 |
| `failureCount` | 현재 누적 실패 횟수 | 임계치 도달 여부 판단 |
| `remainingAttempts` | 임계치까지 남은 횟수 | 운영 위험도 판단 |
| `lockedUntil` | 임시 잠금 만료 시각 | 잠금 해제 예상 시각 |
| `reason` | `INVALID_CREDENTIALS`, `LOCKED_THRESHOLD_REACHED`, `ACCOUNT_TEMPORARILY_LOCKED`, `USER_STATUS_LOCKED`, `USER_DISABLED` | 검색/집계 기준 |
| `path` | 현재 요청 path | 공개 login 경로 확인 |

### login reset 로그

성공 login 으로 failure state를 지웠고 이전 실패 흔적이 있었던 경우에만 `info` 로그를 남깁니다.

| field | 의미 |
| --- | --- |
| `requestId` | 성공 요청 상관키 |
| `loginIdHash` | 같은 loginId 추적용 SHA-256 hash |
| `userId` | 성공 사용자 id |
| `previousFailureCount` | reset 전 실패 누적 횟수 |
| `previousLockedUntil` | reset 전 잠금 만료 시각 |
| `path` | 현재 요청 path |

## Refresh Token Session

공개 인증 경로는 access token JWT와 함께 refresh token rotation을 기본 지원합니다.

- 공개 endpoint:
  - `POST /api/v1/auth/login`
  - `GET /api/v1/auth/sessions`
  - `DELETE /api/v1/auth/sessions/{sessionId}`
  - `DELETE /api/v1/auth/sessions`
  - `POST /api/v1/auth/refresh`
  - `POST /api/v1/auth/logout`
- 응답 필드:
  - `accessToken`
  - `refreshToken`
  - `tokenType`
  - `expiresAt`
  - `refreshExpiresAt`
  - `userId`
- 기본값:
  - `SECURITY_JWT_ACCESS_TOKEN_TTL_SECONDS=900`
  - `SECURITY_JWT_REFRESH_TOKEN_TTL_SECONDS=1209600`
  - `AUTH_REFRESH_TOKEN_SESSION_CLEANUP_ENABLED=true`
  - `AUTH_REFRESH_TOKEN_SESSION_CLEANUP_RETENTION_DAYS=30`
  - `AUTH_REFRESH_TOKEN_SESSION_CLEANUP_BATCH_SIZE=500`
- 저장 기준:
  - raw refresh token은 응답으로만 한 번 내려가고 DB에는 `SHA-256 token_hash`만 저장
  - 저장 테이블은 `auth_refresh_token_session`
  - 성공 refresh 시 기존 row는 `ROTATED`, 새 row는 `ACTIVE`
  - 성공 logout 시 현재 사용자 `ACTIVE` session은 `REVOKED`
  - cleanup batch는 `ACTIVE`는 `expires_at`, `ROTATED|REVOKED`는 `updated_at` 기준으로 retention cutoff 밖 row만 작은 batch로 삭제
- 세션 목록 조회 기준:
  - `GET /api/v1/auth/sessions`
  - 현재 JWT user만 호출 가능하고 bootstrap account principal은 `403`
  - query parameter `size`는 기본 `20`, 최대 `50`
  - 응답은 현재 user의 `ACTIVE` 이면서 아직 만료되지 않은 session만 `expires_at DESC, id DESC` 순서로 반환
  - item 필드는 `sessionId`, `sessionStatus`, `expiresAt`, `lastUsedAt`, `createdAt`
- 세션 종료 기준:
  - `DELETE /api/v1/auth/sessions/{sessionId}`는 현재 user 소유의 `ACTIVE` session 하나만 `REVOKED`로 바꾼다.
  - `DELETE /api/v1/auth/sessions`는 현재 user의 `ACTIVE` session 전체를 `REVOKED`로 바꾼다.
  - 두 endpoint 모두 다른 사용자 session, 이미 `ROTATED|REVOKED` 상태, 존재하지 않는 session에 대해 `204` no-op을 유지한다.
- 거절 기준:
  - 만료, 이미 rotation 된 token, 존재하지 않는 token은 모두 `401 refresh failed`
  - `user_status=LOCKED|DISABLED` 사용자는 refresh로 새 token pair를 발급받지 못함
- 운영 주의:
  - logout은 access token 즉시 폐기가 아니라 refresh 재발급 차단까지만 처리
  - 다른 사용자 token, 이미 `ROTATED|REVOKED` 상태인 token, 존재하지 않는 token으로 logout 요청 시 `204` no-op 유지
  - 선택 revoke와 전체 revoke도 access token 즉시 폐기가 아니라 refresh 재발급 차단까지만 처리
  - raw refresh token, plaintext secret은 로그/DB에 남기지 않음

## Internal Auth Admin Runbook

내부 auth status update는 공개 로그인 경로와 분리된 내부 운영 surface 입니다.

- 공통 endpoint:
  - `PUT /internal/api/v1/auth/users/{userId}/status`
  - `PUT /internal/api/v1/auth/users/{userId}/memberships/{accountId}/status`
- 공통 필수 헤더:
  - `Authorization: Bearer <internal-service-jwt>`
  - `X-Request-Id`: 운영 상관키. 누락 시 서버가 UUID를 생성하지만, 감사 추적 일관성을 위해 운영 호출에서는 직접 넣는 것을 기본값으로 사용합니다.
- token claim 기준:
  - `sub`: 호출 주체 식별값. 예) `ops-admin`, `fraud-batch`
  - `aud`: `aquila-internal-api`
  - `scope`: `internal:auth-admin`
  - `kid`: 서버에 등록된 active/legacy secret 식별값
- 공통 필수 body 필드:
  - `reasonCode`: 분류용 고정 코드. 예) `FRAUD_REVIEW`, `OPS_MANUAL`
  - `reasonDetail`: 운영 문맥 상세값
- legacy compatibility:
  - `reason` 단일 필드는 한시 호환 경로로만 허용되고, 서버 내부에서는 `reasonCode=LEGACY_FREE_TEXT`로 정규화됩니다.

### 준비할 env

앱 실행 기준으로 아래 값이 준비되어 있어야 합니다.

```bash
SECURITY_AUTH_BOOTSTRAP_API_ENABLED=true
SECURITY_INTERNAL_SERVICE_TOKEN_ISSUER=dev-internal-service
SECURITY_INTERNAL_SERVICE_TOKEN_AUDIENCE=aquila-internal-api
SECURITY_INTERNAL_SERVICE_TOKEN_ACTIVE_KEY_ID=ops-202604
SECURITY_INTERNAL_SERVICE_TOKEN_KEYS_OPS_202604=dev-internal-service-secret-ops-202604
SECURITY_INTERNAL_SERVICE_TOKEN_KEYS_OPS_202603=dev-internal-service-secret-ops-202603
```

로컬 실행 예시:

```bash
set -a
source back/.env
set +a
./back/gradlew -p back bootRun
```

운영자 shell 또는 runbook wrapper에는 아래처럼 scope가 포함된 pre-generated token을 준비합니다.

```bash
AUTH_ADMIN_SERVICE_TOKEN='<jwt with sub=ops-admin aud=aquila-internal-api scope=internal:auth-admin>'
```

### 예시 스크립트

운영 호출 예시는 아래 스크립트를 그대로 사용할 수 있습니다.

```bash
tools/ops/internal-auth-update-user-status.sh \
  http://localhost:8080 \
  "$AUTH_ADMIN_SERVICE_TOKEN" \
  auth-user-disable-20260416-001 \
  21 \
  DISABLED \
  FRAUD_REVIEW \
  fraud-review
```

```bash
tools/ops/internal-auth-update-membership-status.sh \
  http://localhost:8080 \
  "$AUTH_ADMIN_SERVICE_TOKEN" \
  auth-membership-revoke-20260416-001 \
  21 \
  1001 \
  REVOKED \
  OPS_MANUAL \
  manual-revoke
```

### 직접 호출 예시

user status update:

```bash
curl --fail-with-body --silent --show-error \
  --request PUT \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${AUTH_ADMIN_SERVICE_TOKEN}" \
  --header "X-Request-Id: auth-user-disable-20260416-001" \
  --data '{"userStatus":"DISABLED","reasonCode":"FRAUD_REVIEW","reasonDetail":"fraud-review"}' \
  "http://localhost:8080/internal/api/v1/auth/users/21/status"
```

membership status update:

```bash
curl --fail-with-body --silent --show-error \
  --request PUT \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${AUTH_ADMIN_SERVICE_TOKEN}" \
  --header "X-Request-Id: auth-membership-revoke-20260416-001" \
  --data '{"membershipStatus":"REVOKED","reasonCode":"OPS_MANUAL","reasonDetail":"manual-revoke"}' \
  "http://localhost:8080/internal/api/v1/auth/users/21/memberships/1001/status"
```

### 실패 조건

- `reasonCode` 누락: `400 Bad Request`
- `reasonDetail` 누락 또는 blank: `400 Bad Request`
- service token 누락, 만료, 서명 오류, audience/scope mismatch: `401 Unauthorized`
- `reason`과 `reasonCode`/`reasonDetail` 동시 사용: `400 Bad Request`
- `X-Request-Id` 누락: 서버가 자동 생성하므로 요청 자체는 실패하지 않음. 다만 운영 감사 추적 키를 맞추기 위해 직접 지정하는 것을 기본값으로 사용

### 운영 주의사항

- `actorSubject` 는 request header가 아니라 service token `sub` 에서 읽습니다. 배치명/운영자 식별값을 짧고 고정된 slug로 발급 단계에서 넣습니다.
- `reasonCode`는 alert/filter 기준으로 쓰고, `reasonDetail`은 감사 로그와 감사 테이블에 남는 운영 문맥으로 사용합니다.
- `reasonDetail`은 길고 자유로운 문장보다 짧은 운영 사유 slug를 우선 사용합니다.
- legacy `reason`은 한시 호환 경로라서 새 운영 호출과 스크립트에서는 사용하지 않는 것을 기본값으로 둡니다.
- `dev`/`test`의 `X-Account-Id` bootstrap fallback은 계좌 요청 테스트용이며, 내부 auth admin status update 인증 방식과 혼용하지 않습니다.

### 실패 감사 로그 필수 필드

실패 감사 로그는 `warn` 레벨 한 줄로 남고, prefix는 항상 `internal auth status update failed`입니다.

| field | 의미 | 운영 사용 기준 |
| --- | --- | --- |
| `requestId` | 호출 상관키 | 장애 drill-down 전용, alert group key로 사용 금지 |
| `httpStatus` | API 응답 status | `401`/`400` alert 분류 1순위 |
| `actorSubject` | 호출 주체 식별값 | 반복 오호출 actor 추적 |
| `targetUserId` | 대상 user 식별자 | 대상 범위 확인 |
| `targetAccountId` | 대상 account 식별자 | membership 경로만 값 존재, user status는 `-` |
| `requestedStatus` | 요청한 상태값 | `DISABLED`, `REVOKED` 같은 실패 의도 확인 |
| `reasonCode` | 분류 코드 | alert/filter 기준 |
| `reasonDetail` | 운영 사유 상세 | 오호출/배치 drift 확인 |
| `path` | 실패 endpoint | user/membership 경로 구분 |
| `error` | 서버가 반환한 실패 원인 | validation/token 오류 분류 |

### 수집 패턴

로그 플랫폼 문법은 각자 다르므로 아래 키 조합만 그대로 매핑합니다.

```text
전체 실패:
  "internal auth status update failed"

401 집계:
  "internal auth status update failed" AND "httpStatus=401"

같은 actorSubject의 400 추적:
  "internal auth status update failed" AND "httpStatus=400" AND "actorSubject=<actor-subject>"

requestId drill-down:
  "internal auth status update failed" AND "requestId=<request-id>"
```

- alert 집계 차원은 `httpStatus`, `actorSubject`, `path`, `error`를 우선 사용합니다.
- `requestId`는 고카디널리티라 alert 집계 기준으로 쓰지 않고 incident drill-down에만 사용합니다.
- `reasonCode` 차원은 `FRAUD_REVIEW`, `OPS_MANUAL`, `LEGACY_FREE_TEXT` 같은 고정값만 사용합니다.
- 구버전 로그가 섞여 `httpStatus`가 없는 기간은 한시적으로 `error=internal service token is invalid`를 `401`, `error=reasonCode is required|reasonDetail is required|reason and reasonCode/reasonDetail cannot be used together`를 `400` fallback으로 사용합니다.

### 최소 alert 기준

- `401 Unauthorized`: `5분 내 3회 이상`이면 warning, 같은 window에서 `10회 이상`이면 critical 후보로 봅니다.
- `400 Bad Request`: 같은 `actorSubject`에서 연속 `2회 이상`이면 warning, `5회 이상`이면 critical 후보로 봅니다.
- `actorSubject=-`인 `400`은 헤더 누락 성격이므로 운영 스크립트 drift 또는 수동 호출 오류로 분류합니다.
- `404`, `409`, `500`은 최소 alert 기본값에서는 제외하고, 아래 후속 운영 기준으로 별도 판단합니다.

### 404/409/500 후속 수집 패턴

#### 404 운영 기준

- 대표 원인:
  - `user is not found`
  - `membership is not found`
- 수집 패턴:

```text
404 후보:
  "internal auth status update failed" AND "httpStatus=404"

같은 actor의 404 반복:
  "internal auth status update failed" AND "httpStatus=404" AND "actorSubject=<actor-subject>" AND "path=<endpoint>"
```

- 운영 의미:
  - stale `userId`/`accountId` 재사용
  - 수동 호출 대상 오입력
  - 배치가 이미 정리된 대상을 늦게 호출한 경우

#### 409 운영 기준

- 현재 known 상태:
  - 내부 auth status update 경로에서 `409` 대표 error 문구는 아직 고정돼 있지 않습니다.
  - 현재 runbook에서는 `httpStatus=409`와 같은 `actorSubject`/`path` 반복 패턴을 먼저 수집하고, 세부 error 분류는 후속 구현 이슈로 남깁니다.
- 수집 패턴:

```text
409 후보:
  "internal auth status update failed" AND "httpStatus=409"

같은 actor의 409 반복:
  "internal auth status update failed" AND "httpStatus=409" AND "actorSubject=<actor-subject>" AND "path=<endpoint>"
```

- 운영 의미:
  - 같은 대상에 대한 중복 상태 변경 시도
  - caller 재시도 정책 또는 수동 재실행 충돌
  - 상태 전이 전후 확인이 필요한 경쟁 조건 후보

#### 500 운영 기준

- 대표 원인:
  - `requestId is not initialized` 같은 서버 상태 불일치
  - persistence/runtime 예외로 인한 internal error
- 수집 패턴:

```text
500 후보:
  "internal auth status update failed" AND "httpStatus=500"

requestId 우선 drill-down:
  "internal auth status update failed" AND "httpStatus=500" AND "requestId=<request-id>"
```

- 운영 의미:
  - 운영 입력 오류보다 서버 측 장애 후보 우선
  - 같은 시간대 `actorSubject`/`path` 확산 여부 확인 필요
  - success audit exact lookup 부재만으로 종료하지 말고 로그 타임라인 재확인이 필요

### 404/409/500 제외/승격 조건

#### 404 운영 기준

- 제외 조건:
  - 단발 `404`이고 같은 actor가 직후 대상 식별자를 수정해 성공 호출로 전환한 경우
  - 수동 점검 과정에서 잘못된 `userId`/`accountId`를 한 번 입력한 경우
- 승격 조건:
  - 같은 `actorSubject + path`에서 동일 target miss가 `10분 내 3회 이상` 반복되면 warning 후보
  - 같은 배치 또는 actor가 여러 target에서 `404`를 확산시키면 critical 후보

#### 409 운영 기준

- 제외 조건:
  - 같은 actor의 단발 중복 호출이고, 인접 시간대에 성공 감사 row가 확인되는 경우
  - 수동 재실행이나 caller retry가 이미 적용된 상태로 보이는 경우
- 승격 조건:
  - 같은 `actorSubject + path + requestedStatus` 조합의 `409`가 `10분 내 3회 이상` 반복되고 success audit row가 확인되지 않으면 warning 후보
  - 서로 다른 actor가 같은 target에 충돌하는 정황이 보이면 critical 후보

#### 500 운영 기준

- 제외 조건:
  - 기본적으로 제외하지 않습니다.
- 승격 조건:
  - `500` 한 건만으로도 incident 후보로 triage 합니다.
  - 같은 시간대에 `500`이 2회 이상 반복되거나 여러 actor/path로 확산되면 critical 후보로 봅니다.

### 404/409/500 requestId 추적 차이

- `404`:
  - `requestId`로 실패 로그 한 줄을 찾은 뒤, 같은 `targetUserId`/`targetAccountId`가 실제로 존재했는지 먼저 확인합니다.
  - success audit row가 없더라도 바로 장애로 보지 않고, stale target 또는 수동 오입력 가능성을 먼저 분리합니다.
- `409`:
  - `requestId`로 실패 시점을 찾은 뒤, 같은 `actorSubject + path + requestedStatus` 조합의 직전/직후 호출을 같이 봅니다.
  - 다른 `requestId`의 success audit row가 근접 시간대에 있으면 중복 호출 또는 재시도 충돌 가능성을 우선 봅니다.
- `500`:
  - `requestId`를 가장 먼저 확보하고, 같은 시간대 `actorSubject + path + error` 확산 여부를 바로 확인합니다.
  - success audit row가 없고 같은 오류가 반복되면 caller 오입력보다 서버 장애 후보로 바로 승격합니다.

### requestId 장애 추적 절차

1. alert 또는 문의에서 `requestId`를 확보합니다. alert payload에 `requestId`가 없으면 같은 시간대 `actorSubject + path + error`로 실패 로그를 먼저 좁힙니다.
2. 앱 로그에서 같은 `requestId`를 재검색해 최초 실패 시점, 재시도 여부, 같은 actor 반복 여부를 확인합니다.
3. 성공 전환 여부가 필요하면 success audit exact lookup으로 같은 `requestId`를 조회합니다.
4. success audit row가 없으면 실패-only incident로 보고 service token claim, request body drift를 runbook 체크리스트로 확인합니다.

success audit exact lookup 예시:

```bash
tools/ops/internal-auth-find-status-change-audit.sh \
  http://localhost:8080 \
  "$AUTH_ADMIN_SERVICE_TOKEN" \
  auth-user-disable-20260416-001
```

- exact lookup은 성공 변경 row만 반환합니다.
- wrapper script 내부에서 exact lookup endpoint, `Authorization: Bearer`, `requestId` query를 고정합니다.
- failure 원본은 structured log이므로 incident 시작점은 항상 로그 검색입니다.

## Outbox Ops Runbook

Kafka producer 를 붙인 이후 outbox backlog 는 actuator health 와 내부 ops 경로를 같이 봐야 복구 판단이 빨라집니다.

- 내부 ops endpoint:
  - `GET /internal/api/v1/outbox/summary`
  - `GET /internal/api/v1/outbox/failed-events?limit=<n>`
  - `POST /internal/api/v1/outbox/recovery/stale-sending`
  - `GET /internal/api/v1/outbox/notification/summary`
  - `GET /internal/api/v1/outbox/notification/dlq-events?limit=<n>`
  - `POST /internal/api/v1/outbox/notification/dlq-events/redrive`
- health endpoint:
  - `GET /actuator/health`

### 준비할 env

```bash
OUTBOX_OPS_ENABLED=true
SECURITY_INTERNAL_SERVICE_TOKEN_ISSUER=dev-internal-service
SECURITY_INTERNAL_SERVICE_TOKEN_AUDIENCE=aquila-internal-api
SECURITY_INTERNAL_SERVICE_TOKEN_ACTIVE_KEY_ID=ops-202604
SECURITY_INTERNAL_SERVICE_TOKEN_KEYS_OPS_202604=dev-internal-service-secret-ops-202604
SECURITY_INTERNAL_SERVICE_TOKEN_KEYS_OPS_202603=dev-internal-service-secret-ops-202603
OUTBOX_OPS_FAILED_LIST_LIMIT=20
OUTBOX_OPS_HEALTH_MAX_LAG_SECONDS=120
OUTBOX_OPS_HEALTH_MAX_FAILED_COUNT=10
OUTBOX_OPS_HEALTH_MAX_STALE_SENDING_COUNT=0
NOTIFICATION_INBOX_CONSUMER_DLQ_TOPIC=bank.transfer.booked.dlq.v1
NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED=true
NOTIFICATION_INBOX_CONSUMER_OPS_HEALTH_MAX_LAG_MESSAGES=100
NOTIFICATION_INBOX_CONSUMER_OPS_HEALTH_MAX_DLQ_COUNT=0
```

운영 호출용 shell에는 아래처럼 pre-generated token을 둡니다.

```bash
OUTBOX_OPS_SERVICE_TOKEN='<jwt with sub=outbox-ops aud=aquila-internal-api scope=internal:outbox-ops>'
```

### 예시 스크립트

failed backlog 조회:

```bash
tools/ops/outbox-find-failed-events.sh \
  http://localhost:8080 \
  "$OUTBOX_OPS_SERVICE_TOKEN" \
  20
```

stale `SENDING` 수동 회수:

```bash
tools/ops/outbox-recover-stale-sending.sh \
  http://localhost:8080 \
  "$OUTBOX_OPS_SERVICE_TOKEN"
```

consumer lag 와 DLQ count summary 조회:

```bash
tools/ops/notification-get-consumer-summary.sh \
  http://localhost:8080 \
  "$OUTBOX_OPS_SERVICE_TOKEN"
```

poison message 최근 항목 조회:

```bash
tools/ops/notification-find-dlq-events.sh \
  http://localhost:8080 \
  "$OUTBOX_OPS_SERVICE_TOKEN" \
  20
```

preview 좌표 기준 DLQ redrive:

```bash
tools/ops/notification-redrive-dlq-event.sh \
  http://localhost:8080 \
  "$OUTBOX_OPS_SERVICE_TOKEN" \
  0 \
  12
```

### 직접 호출 예시

summary 조회:

```bash
curl --fail-with-body --silent --show-error \
  --get \
  --header "Authorization: Bearer ${OUTBOX_OPS_SERVICE_TOKEN}" \
  "http://localhost:8080/internal/api/v1/outbox/summary"
```

notification consumer summary 조회:

```bash
curl --fail-with-body --silent --show-error \
  --get \
  --header "Authorization: Bearer ${OUTBOX_OPS_SERVICE_TOKEN}" \
  "http://localhost:8080/internal/api/v1/outbox/notification/summary"
```

notification DLQ preview 조회:

```bash
curl --fail-with-body --silent --show-error \
  --get \
  --header "Authorization: Bearer ${OUTBOX_OPS_SERVICE_TOKEN}" \
  --data-urlencode "limit=20" \
  "http://localhost:8080/internal/api/v1/outbox/notification/dlq-events"
```

notification DLQ redrive:

```bash
curl --fail-with-body --silent --show-error \
  --request POST \
  --header "Authorization: Bearer ${OUTBOX_OPS_SERVICE_TOKEN}" \
  --header "Content-Type: application/json" \
  --data '{"partition":0,"offset":12}' \
  "http://localhost:8080/internal/api/v1/outbox/notification/dlq-events/redrive"
```

health 확인:

```bash
curl --fail-with-body --silent --show-error \
  --get \
  "http://localhost:8080/actuator/health"
```

### health 상태 해석

- `UP`: outbox `lagSeconds`, `failedCount`, `staleSendingCount` 와 notification `lagCount`, `dlqCount` 가 모두 설정 임계값 이하다.
- `OUT_OF_SERVICE`: outbox backlog 또는 notification consumer lag/DLQ 적재가 임계값을 넘었고 `/actuator/health` 는 `503`으로 내려간다.
- `DOWN`: Kafka admin query 자체가 실패해 lag/DLQ 상태를 계산하지 못한 경우다. broker metadata, topic 존재 여부, group offset 조회 실패를 먼저 본다.
- `OUT_OF_SERVICE` 또는 `DOWN` 이어도 앱 전체 장애와 동일시하지 말고 먼저 `/internal/api/v1/outbox/summary` 와 `/internal/api/v1/outbox/notification/summary` 로 어떤 축이 넘었는지 분리한다.

### 기본 triage 순서

1. `/actuator/health` 가 `503`이면 `/internal/api/v1/outbox/summary` 를 먼저 조회해 `lagSeconds`, `failedCount`, `producerTimeoutFailedCount`, `staleSendingCount` 중 초과 축을 확인합니다.
2. `producerTimeoutFailedCount` 또는 `failedCount` 가 크면 `tools/ops/outbox-find-failed-events.sh` 로 bounded failed list 를 보고 `eventKey`, `retryCount`, `lastError` 를 먼저 확인합니다.
3. `/internal/api/v1/outbox/notification/summary` 또는 `tools/ops/notification-get-consumer-summary.sh` 로 consumer lag 와 DLQ count 를 확인합니다.
4. `dlqCount` 가 0보다 크면 `tools/ops/notification-find-dlq-events.sh` 로 poison message 최근 항목을 보고 `eventKey`, `partition`, `offset`, `originalTopic`, `errorClass`, `errorMessage`, `payloadPreview` 를 먼저 확인합니다.
5. redrive 대상이 명확하면 `tools/ops/notification-redrive-dlq-event.sh` 로 preview -> redrive -> summary 순서로 한 건씩 재처리하고, 응답의 `targetTopic`, `targetPartition`, `targetOffset` 을 기록합니다.
6. `staleSendingCount` 가 0보다 크면 `tools/ops/outbox-recover-stale-sending.sh` 를 한 번만 호출하고, 응답의 `recoveredCount` 와 이후 summary 변화를 확인합니다.
7. recovery 이후에도 `lagSeconds`, `lagCount`, `failedCount` 가 계속 증가하면 producer timeout, consumer 중단, broker 연결 문제를 별도 incident 로 분리합니다.

### 운영 주의사항

- stale recovery 는 직접 publish 가 아니라 stale `SENDING` row 를 `PENDING` 으로 되돌리는 동작입니다.
- recovery 대상은 `outbox.poller.stale-after-seconds` 를 넘긴 row 만 포함합니다.
- failed list 는 `availableAt ASC, id ASC` 순서의 bounded query 이므로, 대량 backlog 에서도 즉시 재시도 대상부터 확인할 수 있습니다.
- `lastError` 는 outbox table 의 짧은 힌트만 남기므로, 상세 stack trace 는 앱 로그와 Kafka client 로그를 같이 봐야 합니다.
- consumer poison message 만 DLQ 로 격리하고, broker/DB 같은 transient failure 는 main topic retry failure 로 남깁니다.
- DLQ preview 는 최근 bounded item 만 보여주므로, 전문 payload/stack trace 가 필요하면 앱 로그와 Kafka client 로그를 같이 확인합니다.
- DLQ redrive 는 source DLQ record 를 삭제하지 않고 original topic 으로 한 번 더 publish 하는 동작입니다.
- payload 또는 참조 데이터가 그대로 잘못돼 있으면 redrive 후 같은 항목이 다시 DLQ 로 돌아올 수 있으므로, preview 에서 `errorClass`, `errorMessage`, `payloadPreview` 를 먼저 확인합니다.

### rollback 기준

- 운영에서 내부 ops 경로를 임시 차단해야 하면 `OUTBOX_OPS_ENABLED=false` 로 내려 endpoint 노출만 끕니다.
- notification consumer ops surface 만 끄려면 `NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED=false` 로 내려 summary/DLQ preview와 health contributor를 함께 끕니다.
- 수동 recovery 가 과도하게 반복되면 더 이상 반복 호출하지 말고 poller 자동 reclaim 과 broker 장애 복구를 먼저 확인합니다.
- 이 runbook 경로는 outbox schema 나 payload 를 수정하지 않으므로, rollback 은 endpoint 비활성화와 PR revert 로 제한합니다.

## Test

```bash
./gradlew test
```

## Java Style

- Google Java Style 기반 포맷 검사는 Spotless로 강제합니다.
- 검사: `./gradlew check`
- 자동 정리: `./gradlew spotlessApply`

## Local Concurrency

- 같은 워크트리에서 여러 스레드가 동시에 `test`/`check`를 돌리면 `back/build/test-results`를 공유해 충돌할 수 있습니다.
- 로컬 검증은 `tools/test/with-resource-lock.sh back-gradle-check ...`로 직렬화하는 것을 기본값으로 사용합니다.
