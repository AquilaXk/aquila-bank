# Aquila Bank Backend

Spring Boot 4 기반 백엔드 애플리케이션입니다.

## Goal

- 초대량 트래픽, 실시간 알림, 1억 건 규모의 거래 조회를 `t3.micro` 환경에서도 원활하게 처리하는 구조를 목표로 합니다.
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

## Login Throttling Storage

- 기본 저장소는 `memory`입니다.
- `redis`는 분산 login throttling 이 필요할 때만 opt-in 으로 사용합니다.
- multi-node 운영에서는 `SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS=true`로 memory fallback 부팅을 차단합니다.
- Redis 경로는 login throttling counter 에만 쓰고, SSE fan-out 은 계속 PostgreSQL `LISTEN/NOTIFY` 를 사용합니다.
- local Redis는 compose `redis` profile로만 뜨며 기본 `postgres kafka` 경로에는 포함하지 않습니다.
- 같은 counter/guard는 password recovery request entrypoint에도 적용되어 token write 전 burst를 차단합니다.

재현 명령:

```bash
tools/test/with-resource-lock.sh back-password-recovery-throttling \
  tools/test/run-password-recovery-throttling.sh
```

## Account List Pagination

`GET /api/v1/accounts` 는 JWT 사용자 요청에서 기존 무파라미터 전체 목록 응답을 유지하면서, `limit` 또는 `cursor` 가 들어오면 account_id 기준 keyset page로 조회합니다.

- 기본 page size: `50`
- 최대 page size: `100`
- cursor: 응답의 `nextCursor` 값을 그대로 다음 요청의 `cursor` 에 전달
- 정렬: `membership.account_id ASC`
- index: `idx_user_account_membership_user_status_account_cursor`

재현 명령:

```bash
tools/test/with-resource-lock.sh back-account-list-keyset \
  tools/test/run-account-list-keyset-pagination.sh
```

## Bootstrap Bulk Import

내부 운영/테스트 도구는 `POST /internal/api/v1/bootstrap/bulk-import` 로 작은 bootstrap batch를 한 transaction에서 가져올 수 있습니다.

- required scopes: `internal:account-bootstrap`, `internal:auth-bootstrap`
- section order: `accounts` -> `users` -> `memberships`
- max section size: 각 `50`개
- membership은 같은 요청의 `userRef`, `accountRef`를 참조합니다.
- 중간 실패 시 같은 요청의 account/user/membership 변경은 rollback 됩니다.

재현 명령:

```bash
tools/test/with-resource-lock.sh back-bootstrap-bulk-import \
  tools/test/run-bootstrap-bulk-import-api.sh
```

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

## Transaction Read Replica Routing

transaction read replica baseline은 거래 조회 3개 path만 좁게 분리합니다.

- 적용 범위:
  - `GET /api/v1/transactions`
  - `GET /api/v1/transactions/{transactionReference}`
  - `GET /api/v1/transactions/archive`
- wiring:
  - `JdbcTransactionReadRepository`
  - `JdbcTransactionDetailRepository`
  - `JdbcTransactionArchiveReadRepository`
  - `RoutedTransactionReadRepository`
  - `RoutedTransactionDetailRepository`
  - `RoutedTransactionArchiveReadRepository`
  - 전용 `transactionReadJdbcTemplate`
  - 전용 `transactionReadTransactionManager`
- fallback:
  - `TRANSACTION_READ_REPLICA_ENABLED=false`
  - 또는 `TRANSACTION_READ_REPLICA_URL` 미설정
  - replica lag probe 실패 또는 replay timestamp unknown
  - `TRANSACTION_READ_REPLICA_LAG_THRESHOLD_MS` 초과
  - 위 경우 기존처럼 primary만 사용
- routing policy:
  - detail exact lookup과 `transactionReference` exact timeline lookup은 primary
  - 첫 page hot window(`to >= now - TRANSACTION_READ_REPLICA_HOT_READ_WINDOW_MS`)는 primary
  - old first page, cursor page, archive 조회는 replica lag가 healthy일 때만 replica
  - local/test처럼 같은 DB를 replica URL로 둔 경우 `pg_is_in_recovery()=false`를 lag 0으로 처리
- 기본 replica pool:
  - `minimumIdle=0`
  - `maximumPoolSize=2`
- 운영 주의:
  - account/auth/write path는 계속 primary를 사용합니다.
  - 거래 직후 강한 read-after-write 일관성이 필요한 확인 흐름은 detail/reference/hot first page primary 정책으로 보호합니다.
  - lag probe는 `TRANSACTION_READ_REPLICA_PROBE_CACHE_MS` 동안 cache해 고트래픽에서 probe query 비용을 제한합니다.
- 주요 설정:
  - `TRANSACTION_READ_REPLICA_ENABLED`
  - `TRANSACTION_READ_REPLICA_URL`
  - `TRANSACTION_READ_REPLICA_USERNAME`
  - `TRANSACTION_READ_REPLICA_PASSWORD`
  - `TRANSACTION_READ_REPLICA_POOL_MAX_SIZE`
  - `TRANSACTION_READ_REPLICA_STATEMENT_TIMEOUT_MS`
  - `TRANSACTION_READ_REPLICA_LOCK_TIMEOUT_MS`
  - `TRANSACTION_READ_REPLICA_LAG_THRESHOLD_MS`
  - `TRANSACTION_READ_REPLICA_PROBE_CACHE_MS`
  - `TRANSACTION_READ_REPLICA_HOT_READ_WINDOW_MS`
- metric:
  - `aquila_transaction_read_replica_lag_ms`
  - `aquila_transaction_read_replica_route_decisions_total{query_shape="timeline|detail|archive",route="primary|replica",reason="..."}`

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

### Transaction Archive Query

`transaction_read_model_archive` 로 이동한 cold history 는 hot 조회와 분리해 `GET /api/v1/transactions/archive` 로 조회합니다.

- hot path:
  - `GET /api/v1/transactions`
  - table: `transaction_read_model`
  - 목적: 최근/활성 거래 timeline 조회
- cold path:
  - `GET /api/v1/transactions/archive`
  - table: `transaction_read_model_archive`
  - 목적: retention cleanup 이후 archive projection 조회
- 두 endpoint 모두 같은 조회 계약을 씁니다.
  - `accountId`, `from`, `to`, `limit`, `cursor`
  - `status`, `direction`, `minAmountMinor`, `maxAmountMinor`, `transactionReference`
  - 기간 상한 `31일`
  - 정렬 `booked_at DESC, id DESC`
- archive 조회는 hot table과 union하지 않습니다. 클라이언트는 조회 기간과 보존 정책에 맞춰 hot/cold endpoint를 선택합니다.
- archive index:
  - `idx_transaction_read_model_archive_account_cursor`
  - `idx_transaction_read_model_archive_account_status_cursor`
  - `idx_transaction_read_model_archive_account_reference_cursor`
- rollback 기준:
  - API rollback은 PR revert로 수행합니다.
  - `V43__add_transaction_archive_reference_index.sql` 로 추가된 reference index 제거가 필요한지 DB 상태를 확인합니다.

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
- Redis profile 기본 포트: `localhost:6379`
- 로컬 Kafka broker는 topic auto-create를 끄고, app startup provisioning이 configured topic을 명시적으로 준비합니다.
- 로컬 기본 baseline은 `KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR=1`, `KAFKA_TOPIC_PROVISIONING_MIN_IN_SYNC_REPLICAS=1` 입니다.
- 기본 topic 이름은 `bank.notification.outbox.v1`, `bank.transfer.booked.v1`, `bank.transfer.reversed.v1`, `bank.transfer.booked.dlq.v1` 입니다.
- startup validation은 configured topic 존재, 최소 partition 수, replication factor, `min.insync.replicas`를 확인하고, outbox/consumer bootstrap server가 다르면 fail-fast 합니다.
- topic partition을 늘릴 때는 `KAFKA_TOPIC_PROVISIONING_PARTITIONS`와 `NOTIFICATION_INBOX_CONSUMER_CONCURRENCY`를 같이 조정합니다.
- 이 경로는 로컬 개발 전용입니다.

t3.micro에 가까운 작은 로컬 인프라 budget으로 띄울 때는 override 파일을 함께 지정합니다.

```bash
docker compose -f compose.yml -f compose.t3micro.yml up -d postgres kafka
```

- [compose.t3micro.yml](/Users/aquila/Custom/GitProjects/aquila-bank/compose.t3micro.yml)은 Postgres/Kafka/Redis에 CPU, memory, swap, pids 상한을 겁니다.
- 이 override는 로컬 병목 신호를 빨리 보기 위한 근사값이며, AWS `t3.micro`의 CPU credit, EBS 지연, 실제 네트워크를 재현하지 않습니다.
- Redis까지 같은 budget으로 올릴 때는 `docker compose -f compose.yml -f compose.t3micro.yml --profile redis up -d redis`를 사용합니다.

Redis login throttling runtime smoke가 필요할 때만 profile을 켭니다.

```bash
docker compose --profile redis up -d redis
```

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
- `dev` 프로필은 `OUTBOX_KAFKA_ENABLED=true`, `NOTIFICATION_INBOX_CONSUMER_ENABLED=true`만 주면 `localhost:9092`, 기본 topic 이름, provisioning/validation 기본값을 자동 사용합니다.
- Kafka 포트를 바꾸면 `OUTBOX_KAFKA_BOOTSTRAP_SERVERS`, `NOTIFICATION_INBOX_CONSUMER_BOOTSTRAP_SERVERS`를 같은 값으로 같이 넘깁니다.
- `NOTIFICATION_INBOX_CONSUMER_CONCURRENCY` 기본값은 `1`입니다. partition 확장 검증 없이 값을 올리지 않고, 운영에서는 topic partition 수와 DB pool 여유 안에서만 올립니다.
- multi-broker 운영 baseline은 `KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR=3`, `KAFKA_TOPIC_PROVISIONING_MIN_IN_SYNC_REPLICAS=2`, `OUTBOX_KAFKA_PRODUCER_ACKS=all`, `OUTBOX_KAFKA_PRODUCER_ENABLE_IDEMPOTENCE=true` 조합을 기본값으로 둡니다.
- broker 수가 replication factor 보다 적거나 topic의 실제 replication/min ISR 이 baseline 과 다르면 startup validation 이 fail-fast 합니다.
- provisioning/validation을 끄려면 `KAFKA_TOPIC_PROVISIONING_ENABLED=false`, `KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED=false`를 함께 조정합니다.

## Prometheus Metrics

- export endpoint:
  - `GET /actuator/prometheus`
- 보안 기준:
  - application security는 `/actuator/prometheus`를 permitAll 로 열어 Prometheus scrape를 단순화합니다.
  - 운영에서는 security group, private subnet, Nginx allowlist 같은 네트워크 경계로 외부 공개를 막는 것을 기본값으로 둡니다.
- custom metric:
  - `aquila_outbox_dispatch_lag_seconds`
  - `aquila_outbox_failed_count`
  - `aquila_outbox_quarantined_count`
  - `aquila_outbox_failed_producer_timeout_count`
  - `aquila_outbox_sending_stale_count`
  - `aquila_notification_consumer_lag_count`
  - `aquila_notification_consumer_dlq_count`
  - `aquila_notification_sse_sessions{principal_type="account|user|total"}`
  - `aquila_auth_throttling_reject_count_total{entry_point="login|password_recovery",scope="ip|global",store="memory|redis"}`
  - `aquila_auth_current_session_gate_reject_count_total{reason_code="MISSING_SESSION_ID|INACTIVE_OR_MISMATCHED_SESSION"}`
  - `aquila_auth_refresh_token_reuse_detected_count_total{reason_code="ROTATED_TOKEN_REUSE"}`
  - `aquila_api_admission_requests_total{group="transaction-read|account-read|transfer-write|notification-stream|notification-read|internal-ops",outcome="accepted|rejected"}`
  - `aquila_api_admission_inflight{group="transaction-read|account-read|transfer-write|notification-stream|notification-read|internal-ops"}`
  - `aquila_t3micro_saturation_guard_requests_total{outcome="accepted|rejected"}`
  - `aquila_t3micro_saturation_guard_saturated`
  - `aquila_command_idempotency_started_count`
  - `aquila_command_idempotency_stale_started_count`
  - `aquila_command_idempotency_completed_count`
  - `aquila_command_idempotency_failed_count`
  - `aquila_command_idempotency_cleanup_candidate_count`
  - `aquila_command_idempotency_conflict_count_total{reason_code="DIFFERENT_REQUEST|IN_PROGRESS|STATE_MISSING|ALREADY_REVERSED|AMOUNT_EXCEEDED|OTHER"}`
  - `aquila_command_idempotency_recovery_recovered_count_total`
  - `aquila_command_idempotency_cleanup_deleted_count_total`
  - `aquila_provider_delivery_status_count{queue="notification_channel|password_recovery",status="sent|skipped|failed|quarantined"}`
  - `aquila_provider_delivery_skip_reason_count{queue="notification_channel|password_recovery",reason="..."}`
  - `aquila_provider_delivery_retry_backlog_count{queue="notification_channel|password_recovery"}`
  - `aquila_t3micro_saturation_guard_query_timeouts_total`
  - `aquila_transaction_query_latency_seconds`
  - `aquila_transaction_read_replica_lag_ms`
  - `aquila_transaction_read_replica_route_decisions_total{query_shape="timeline|detail|archive",route="primary|replica",reason="..."}`
- DB saturation metric:
  - backend actuator scrape: `hikaricp_connections_pending`, `hikaricp_connections_active`, `hikaricp_connections_max`
  - Postgres exporter scrape: `pg_stat_activity_lock_waiting_count`, `pg_stat_statements_seconds_total`, `pg_stat_statements_calls_total`
- 활성화 조건:
  - outbox metric은 기본 wiring만 있으면 항상 export 됩니다.
  - notification consumer lag/DLQ metric은 `NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED=true` 와 DLQ topic 설정이 있어야 export 됩니다.
  - current session gate reject counter는 민감 mutation에서 legacy JWT `session_id` 누락 또는 inactive/mismatch session 차단이 발생하면 증가합니다.
  - auth throttling reject metric은 login/password recovery edge 직전 reject가 발생하면 `entry_point`, `scope`, `store` tag 기준으로 증가합니다.
  - refresh token reuse metric은 `ROTATED` refresh token 재사용 감지와 family revoke가 발생하면 증가합니다.
  - admission control metric은 MVC edge에서 path group별 accepted/rejected와 in-flight를 바로 기록합니다.
  - t3 saturation guard request/saturated metric은 protected path fail-fast 여부를 기록하고, query timeout counter는 DB timeout 신호를 따로 누적합니다.
  - command idempotency summary gauge는 ops summary를 5초 cache로 재사용해 stale/failed/cleanup candidate 숫자를 export 합니다.
  - command idempotency conflict counter는 transfer/reversal conflict를 `reason_code` 축으로만 누적합니다.
  - command idempotency recovery/cleanup counter는 stale recovery row 수, retention cleanup delete row 수를 누적합니다.
  - provider delivery gauge는 notification/password recovery delivery table의 sent/skipped/failed/quarantined/retry backlog row 수와 skip reason을 5초 cache로 export 합니다.
  - t3.micro query timeout counter는 backend query timeout exception이 발생하면 증가합니다.
  - Hikari pool metric은 Spring Boot/Micrometer 기본 binder와 `aquila-bank-pool` pool tag 기준으로 export 됩니다.
  - Postgres exporter `pg_stat_statements_*`는 `pg_stat_statements` extension과 collector 활성화가 필요합니다.
  - `pg_stat_activity_lock_waiting_count`는 `wait_event_type = 'Lock'` custom query metric으로 둡니다.
  - transaction latency timer는 `GET /api/v1/transactions` query path가 한 번이라도 호출되면 `query_shape` tag 기준으로 누적됩니다.
  - transaction latency histogram bucket은 p95 SLO alert용으로 `50ms, 80ms, 120ms, 150ms, 180ms, 350ms, 750ms, 1s, 3s` 경계를 export 합니다.
  - transaction read replica lag gauge는 lag probe가 한 번이라도 실행되면 최신 lag ms를 유지합니다.
  - transaction read replica route counter는 detail/timeline/archive decision을 low-cardinality tag로 누적합니다.
- transaction `query_shape` 기준:
  - `first_page`
  - `cursor`
  - `status_first`, `status_cursor`
  - `direction_first`, `direction_cursor`
  - `amount_first`, `amount_cursor`
  - `mixed_first`, `mixed_cursor`
  - `reference_exact`
- transaction p95 SLO 기준:
  - `reference_exact`: `80ms`
  - `first_page`: `120ms`
  - `cursor`, `status_first`, `status_cursor`, `direction_first`, `direction_cursor`: `150ms`
  - `amount_first`, `amount_cursor`, `mixed_first`, `mixed_cursor`: `180ms`
- 운영 메모:
  - outbox/notification gauge는 scrape 한 번에 같은 summary를 여러 번 다시 조회하지 않게 `5초` cache 안에서 재사용합니다.
  - provider delivery metric label은 `queue`, `status`, `reason`만 사용하고, `userId`, `requestId`, `eventKey`, destination은 table/log drill-down에서만 확인합니다.
  - SSE session metric은 현재 app instance 메모리의 active session 수만 보여주므로 multi-instance 전체 합계는 Prometheus 쿼리에서 합산합니다.
  - auth throttling reject metric label은 `entry_point`, `scope`, `store`만 사용하고, IP, `requestId`, `userId`, `path`는 structured log에서만 확인합니다.
  - `AquilaAuthThrottlingRejectBurstDetected` alert는 brute-force/abuse 징후 investigation 시작점입니다. 같은 시간대 `auth login throttled`, `auth password recovery throttled` log와 Nginx auth edge `429` 추이를 같이 봅니다.
  - current session gate reject metric label은 `reason_code`만 사용하고, `requestId`, `userId`, `sessionId`, `path`는 structured audit log에서만 확인합니다.
  - `AquilaCurrentSessionActiveGateRejectDetected` alert는 장애 확정이 아니라 security investigation 시작점입니다. 같은 시간대 `requestId`로 `auth current session gate rejected` log를 조회하고, `reasonCode`, `userId`, `sessionId`, `method`, `path`를 확인합니다.
  - current session gate alert rollback은 `AquilaCurrentSessionActiveGateRejectDetected` rule 제거 또는 threshold/`for` 시간 조정으로 수행하고, API 응답 계약은 그대로 유지합니다.
  - refresh token reuse metric label은 `reason_code`만 사용하고, `requestId`, `userId`, `reusedSessionId`, `familyRootId`는 structured audit log에서만 확인합니다.
  - `AquilaRefreshTokenReuseDetected` alert는 공격성 재사용 후보입니다. 같은 시간대 `requestId`로 `auth refresh token reuse detected` log를 조회하고 `reusedSessionId`, `familyRootId`, `revokedCount`를 먼저 확인합니다.
  - admission control metric label은 `group`, `outcome`만 사용하고 IP/path/requestId는 edge log나 app log에서 drill-down 합니다.
  - `AquilaApiAdmissionRejectBurstDetected`는 endpoint group reject burst를 의미합니다. 같은 시간대 `aquila_api_admission_inflight`, auth/Nginx throttling, t3 saturation signal을 같이 봅니다.
  - `AquilaT3MicroSaturationRejectDetected`는 protected path fail-fast가 실제로 발생한 상태입니다. pool pending/active, servlet busy, query timeout, slow query를 같은 시간대에서 바로 확인합니다.
  - auth throttling alert rollback은 `AquilaAuthThrottlingRejectBurstDetected` rule 제거 또는 threshold/`for` 시간 조정으로 수행하고, auth API 응답 계약은 그대로 유지합니다.
  - command idempotency conflict metric label은 `reason_code`만 사용하고 idempotency key, transaction reference, account id는 API/app log 또는 ops API에서 drill-down 합니다.
  - command idempotency summary gauge는 `ledger.command-idempotency.ops.enabled=true`일 때 live count를 export 하고, 비활성 상태에서는 zero baseline만 남깁니다.
  - p95 alert는 `query_shape`별 5분 rate가 충분할 때만 평가해 low traffic 노이즈를 줄입니다.
  - `AquilaDbPoolPendingWaitDetected`는 Hikari pending connection이 남은 상태라 lock wait, slow query, DB CPU, transaction p95를 같은 시간대에서 같이 확인합니다.
  - `AquilaDbPoolActivePressureHigh`는 active/max pool ratio 90% 이상을 queueing 전조로 봅니다. t3.micro 기본 `DB_POOL_MAX_SIZE=4`에서는 순간 spike보다 10분 지속 여부가 중요합니다.
  - `AquilaDbQueryTimeoutDetected`는 `statement_timeout` 또는 `lock_timeout` 전파 신호로 보고 blocking query와 pool pending을 먼저 좁힙니다.
  - `AquilaPostgresLockWaitDetected`는 Postgres exporter custom metric이 있을 때만 동작합니다. alert label에는 query text/pid/user/requestId를 올리지 않고 DB drill-down에서 확인합니다.
  - `AquilaPostgresSlowQueryDetected`는 `pg_stat_statements` database-level 평균이 750ms를 넘는지 보는 coarse guard입니다. query별 확인은 `queryid` 기준으로 별도 조회합니다.
  - refresh token reuse alert rollback은 `AquilaRefreshTokenReuseDetected` rule 제거 또는 notification routing 비활성화로 수행하고, refresh API 응답 계약은 그대로 유지합니다.
  - admission/t3 guard alert rollback은 `AquilaApiAdmissionRejectBurstDetected`, `AquilaT3MicroSaturationRejectDetected` rule 제거 또는 threshold/`for` 시간 조정으로 수행합니다.
  - command idempotency metric rollback은 `CommandIdempotencyPrometheusMetrics` wiring 제거와 recovery/cleanup/conflict recorder 호출 제거로 수행합니다.
  - provider delivery metric rollback은 `ProviderDeliveryPrometheusMetrics` wiring 제거로 제한하며 delivery outbox 상태 전이는 유지합니다.
  - histogram bucket/alert rollback은 `management.metrics.distribution.*.aquila.transaction.query.latency`와 `AquilaTransactionQueryLatencyP95SloHigh` rule 제거로 수행합니다.
  - DB saturation alert rollback은 `ops/prometheus/rules/aquila-bank-alerts.yml`의 `aquila-bank-postgres` group 제거 또는 threshold/`for` 시간 조정으로 수행합니다.
  - baseline 자산은 `ops/prometheus/` 아래에 두고 dashboard import, alert rule apply, tuning 가이드는 `ops/prometheus/README.md`를 기준으로 봅니다.
- baseline 파일:
  - dashboard: `ops/prometheus/grafana/aquila-bank-overview.json`
  - alert rule: `ops/prometheus/rules/aquila-bank-alerts.yml`
  - validation: `bash tools/ops/validate-prometheus-assets.sh`

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

### Kafka Consumer Partition Concurrency

topic partition을 늘려도 consumer concurrency가 `1`이면 lag 해소 속도는 단일 listener thread에 묶입니다. partition 확장 전후에는 아래 smoke로 같은 fixture에서 concurrency `1`과 `4`를 비교합니다.

```bash
tools/test/run-kafka-consumer-partition-concurrency.sh
```

- fixture: 4 partitions, 48 records, listener당 고정 30ms work
- 목표: 최종 consumer lag `0`, concurrency `4` throughput 이 concurrency `1`보다 `1.5x` 초과
- 운영 적용: `KAFKA_TOPIC_PROVISIONING_PARTITIONS=<n>`으로 topic 최소 partition을 올리고 `NOTIFICATION_INBOX_CONSUMER_CONCURRENCY=<n>`은 partition 수 이하로 둡니다.
- t3.micro 기준: DB write path가 같이 느려질 수 있으므로 consumer lag, Hikari pool pending, `AquilaDbPoolActivePressureHigh`를 함께 확인합니다.
- rollback: lag가 줄지 않거나 DB pool wait가 늘면 `NOTIFICATION_INBOX_CONSUMER_CONCURRENCY=1`로 되돌리고 partition 증설 효과를 재측정합니다.

### Kafka Production Replication Baseline

운영 Kafka 는 topic partition 수만 맞추면 끝나지 않습니다. producer 기본값이 `acks=all`, idempotence on 이므로 topic replication factor 와 `min.insync.replicas` baseline 이 같이 맞아야 broker 장애 시에도 durability/availability trade-off 가 예측 가능합니다.

```bash
tools/test/run-kafka-production-replication-baseline.sh
```

- local single broker baseline: `KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR=1`, `KAFKA_TOPIC_PROVISIONING_MIN_IN_SYNC_REPLICAS=1`
- multi-broker 운영 baseline: `KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR=3`, `KAFKA_TOPIC_PROVISIONING_MIN_IN_SYNC_REPLICAS=2`
- startup validation 은 required broker 수, topic 최소 partition 수, topic replication factor, topic `min.insync.replicas`를 같이 확인합니다.
- rollback: broker 수를 줄이거나 quorum 정책을 완화해야 하면 topic baseline env 와 실제 topic config 를 같이 낮춘 뒤 재시작합니다.

### Production t3.micro Capacity Smoke

production budget 회귀는 아래 smoke entrypoint와 scheduled workflow로 주기 확인합니다.

```bash
tools/test/run-production-t3micro-capacity-smoke.sh
```

- 위 script는 기존 `tools/test/run-t3micro-mixed-workload-soak.sh`를 production budget env와 함께 실행합니다.
- 기본 budget은 `DB_POOL_MAX_SIZE=4`, `SERVER_THREADS_MAX=16`, `NOTIFICATION_SSE_MAX_TOTAL_SESSIONS=64`, `OPS_API_ADMISSION_CONTROL_NOTIFICATION_STREAM_MAX=4` 입니다.
- 기본 repeat는 `1`이고, 장시간 rehearsal이 필요하면 `SOAK_REPEAT=<n>`으로 늘립니다.
- scheduled workflow `Production t3.micro Capacity Smoke`는 매주 월요일 03:15 KST(`15 18 * * 0` UTC)와 manual `workflow_dispatch`를 지원합니다.
- repository variable로 아래 값을 override 할 수 있습니다.
  - `PRODUCTION_T3MICRO_SOAK_REPEAT`
  - `PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE`
  - `PRODUCTION_T3MICRO_SERVER_THREADS_MAX`
  - `PRODUCTION_T3MICRO_SSE_MAX_TOTAL_SESSIONS`
  - `PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX`
- smoke rollback은 workflow 비활성화 또는 variable 값을 기본 budget으로 되돌리는 방식으로 처리합니다.

로컬에서 Docker cgroup 제한까지 걸어 빠르게 회귀를 확인할 때는 아래 entrypoint를 사용합니다.

```bash
tools/test/run-docker-t3micro-capacity-smoke.sh --print-plan
tools/test/run-docker-t3micro-capacity-smoke.sh
```

- 기본 Docker budget은 `--cpus=2`, `--memory=1024m`, `--memory-swap=1024m`, `--pids-limit=384` 입니다.
- script는 새 부하 발생기를 만들지 않고 기존 `tools/test/run-production-t3micro-capacity-smoke.sh`를 container 안에서 재사용합니다.
- 기본값은 실행 전 host에서 `testClasses`를 준비해 Gradle compile 비용을 Docker 1GiB 판정에서 분리합니다. 이 동작을 끄려면 `DOCKER_T3MICRO_PREPARE_TEST_CLASSES=false`를 사용합니다.
- 기본 image는 `eclipse-temurin:21-jdk`이고, 로컬에 다른 Java 21 image가 있으면 `DOCKER_T3MICRO_IMAGE=<image>`로 바꿀 수 있습니다.
- Docker smoke는 host 자원이 큰 개발 머신에서 놓칠 수 있는 JVM/thread/pool 압력 회귀를 빨리 잡는 용도입니다.
- 최종 120% headroom 판정은 실제 EC2 `t3.micro` staging에서 transaction replay, read replica smoke, production capacity smoke를 실행한 결과로 닫습니다.

### k6 Transaction 100m Load Test

1억 건 분포가 준비된 transaction read model을 HTTP로 replay할 때는 k6 loadtest overlay를 사용합니다.

```bash
K6_HOT_ACCOUNT_ID=101 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=202 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
tools/test/run-k6-transaction-100m-loadtest.sh
```

- 실행 전 `./back/gradlew -p back bootJar`는 runner가 자동 수행합니다.
- backend는 `compose.loadtest.yml`의 `aquila-bank-backend` service로 실행되고 기본 `2 vCPU / 1GiB` budget을 적용받습니다.
- k6는 hot/cold first page와 cursor page를 호출하고, 기본 threshold는 hot p95 `350ms`, cold p95 `750ms`, HTTP failed rate `< 1%` 입니다.
- Prometheus는 `http://localhost:9090`, Grafana는 `http://localhost:3001`로 노출됩니다.
- k6 summary 원본은 `build/reports/k6`, 리뷰용 Markdown은 `docs/performance-results`에 남깁니다.
- 이 runner는 1억 row를 적재하지 않습니다. `K6_*_ACCOUNT_ID`와 기간은 이미 데이터가 준비된 local/staging dataset에 맞춰 넣어야 합니다.

## Notification Read State

- JWT user 경로의 읽음 상태는 `notification_user_read_state`에 user별로 저장됩니다.
- JWT user 경로의 bulk archive/delete 도 같은 테이블의 `archived_at`, `deleted_at` 으로 user별 상태를 분리해 공동 사용자 inbox를 보존합니다.
- `notification_inbox`는 account-scoped read model 본체를 유지하고, `read_at`은 account principal/internal 경로 의미로 분리됩니다.
- account principal/internal 경로의 bulk archive 는 `notification_inbox.archived_at` 으로 숨기고, bulk delete 는 inbox row 자체를 제거합니다.
- 기존 shared `read_at` 값은 user read state로 자동 backfill 하지 않으므로, 배포 이전 알림은 JWT user 기준에서 다시 unread로 보일 수 있습니다.
- archived/deleted 상태는 `GET /api/v1/notifications`, unread count, SSE replay pull 경로에서 기본 제외됩니다.
- notification inbox retention cleanup은 `notification_inbox.created_at` 기준으로만 동작해 account/user 경로의 read 의미를 따로 해석하지 않습니다.
- 오래된 inbox row를 지울 때 연결된 `notification_user_read_state`도 FK cascade로 함께 정리해 read state orphan과 unread 회귀를 막습니다.
- 기본 설정은 `NOTIFICATION_INBOX_CLEANUP_ENABLED=true`, `NOTIFICATION_INBOX_CLEANUP_RETENTION_DAYS=90`, `NOTIFICATION_INBOX_CLEANUP_BATCH_SIZE=500`, `NOTIFICATION_INBOX_CLEANUP_FIXED_DELAY_MS=300000` 입니다.
- JWT user inbox list는 active account별 bounded LATERAL query로 `idx_notification_inbox_account_visible_cursor` partial index를 재사용합니다.
- user inbox EXPLAIN baseline은 active membership/user visibility와 per-user hidden state를 포함한 first/cursor page에서 `notification_inbox` full scan 회귀를 차단합니다.
- 실행:
  ```bash
  tools/test/with-resource-lock.sh back-notification-user-inbox-baseline \
    tools/test/run-notification-user-inbox-explain-baseline.sh
  ```

## Notification Search API

- endpoint:
  - `GET /api/v1/notifications/search`
- query param:
  - `limit`, `cursor`
  - `readStatus=ALL|UNREAD|READ` 기본값 `ALL`
  - `eventType` exact match
  - `from`, `to`는 ISO-8601 UTC instant 두 값이 함께 와야 합니다.
- 계약:
  - 기존 `GET /api/v1/notifications` 목록 API는 그대로 유지하고, 검색은 별도 endpoint로 분리합니다.
  - `from/to`를 생략하면 최근 31일 window를 자동 적용하고, 응답 `appliedFrom`, `appliedTo`에 실제 window를 반환합니다.
  - 다음 page는 `cursor`만 보내도 같은 `appliedFrom/appliedTo`를 재사용합니다.
  - 검색 cursor는 `readStatus`, `eventType`, `appliedFrom`, `appliedTo` fingerprint를 포함하므로 요청 필터가 달라지면 `400`을 반환합니다.
  - `from > to` 또는 31일 초과 window는 허용하지 않습니다.

## Notification Bulk Actions

- endpoint:
  - `POST /api/v1/notifications/read`
  - `POST /api/v1/notifications/archive`
  - `POST /api/v1/notifications/delete`
- request body:
  - `{"notificationIds":[10,11,12]}`
- 계약:
  - `notificationIds` 는 1건 이상 100건 이하만 허용합니다.
  - 범위 밖 id 는 not found 로 드러내지 않고 무시합니다.
  - JWT user bulk delete 는 shared `notification_inbox` row hard delete 대신 per-user `deleted_at` 숨김으로 처리합니다.
  - account principal bulk delete 만 `notification_inbox` row hard delete 를 수행합니다.
  - bulk archive/delete 대상은 후속 list/unread-count/replay 조회에서 기본 제외됩니다.

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
- 부하/장애 주입 검증:
  - account 2개 session, user 2개 session, live event 25건을 같은 broker publish path로 보내 누락과 순서를 확인합니다.
  - emitter send 실패는 해당 session만 제거하고 같은 batch의 healthy session 전파를 유지해야 합니다.
  - replay pending overflow는 `pending_overflow` drop metric 증가와 session 제거로 해석합니다.
  - 실행:
    ```bash
    tools/test/with-resource-lock.sh back-notification-sse-fanout-load-fault \
      tools/test/run-notification-sse-fanout-load-fault.sh
    ```

## Notification Channel Provider Delivery

- worker는 `notification_channel_outbox` row를 claim 한 뒤 `NotificationChannelProviderPort`로 EMAIL/SMS 외부 delivery를 시도합니다.
- 기본값은 `LoggingNotificationChannelProvider` fallback 이고, `NOTIFICATION_CHANNEL_PROVIDER_DELIVERY_ENABLED=true`일 때 실제 webhook provider adapter가 활성화됩니다.
- 실제 provider destination은 `bank_user_verified_contact`의 `user_id + contact_channel` row에서 조회합니다.
  - `EMAIL` channel: `contact_channel='EMAIL'`의 `provider_destination`
  - `SMS` channel: `contact_channel='SMS'`의 `provider_destination`
- `bank_user_verified_contact`는 `user_id + contact_channel` unique 기준이며, migration 시 active user의 email/E.164 `login_id`만 초기 verified contact로 backfill 합니다.
- 내부 운영 경로는 `GET|PUT|DELETE /internal/api/v1/auth/users/{userId}/verified-contacts[/{$contactChannel}]`이고 `internal:auth-admin` scope가 필요합니다.
- verified contact lookup miss, inactive user, channel URL 누락은 잘못된 외부 발송 대신 fail-safe skip 처리하고 row는 `SKIPPED`와 `skip_reason`으로 정리합니다.
- webhook timeout, 4xx/5xx, network error 같은 실제 provider 장애만 예외로 전파돼 기존 bounded retry/backoff/quarantine 흐름으로 들어갑니다.
- webhook 요청 공통 header는 `NOTIFICATION_CHANNEL_PROVIDER_DELIVERY_AUTH_HEADER_NAME`, `NOTIFICATION_CHANNEL_PROVIDER_DELIVERY_AUTH_HEADER_VALUE`로 주입합니다.
- timeout은 `NOTIFICATION_CHANNEL_PROVIDER_DELIVERY_CONNECT_TIMEOUT_MS`, `NOTIFICATION_CHANNEL_PROVIDER_DELIVERY_READ_TIMEOUT_MS`로 조정합니다.
- channel별 URL은 아래 env를 사용합니다.
  - `NOTIFICATION_CHANNEL_PROVIDER_DELIVERY_EMAIL_URL`
  - `NOTIFICATION_CHANNEL_PROVIDER_DELIVERY_SMS_URL`
- webhook payload는 `channel`, `deliveryKey`, `notificationId`, `userId`, `accountId`, `category`, `eventType`, `destination`, `payload` 필드를 포함합니다.
- `deliveryKey`는 기존 outbox `eventKey`를 그대로 사용합니다. provider idempotency key도 같은 값을 우선 사용합니다.
- 운영 기본선:
  - `NOTIFICATION_CHANNEL_PROVIDER_DELIVERY_ENABLED=false`
  - `NOTIFICATION_CHANNEL_PROVIDER_DELIVERY_CONNECT_TIMEOUT_MS=3000`
  - `NOTIFICATION_CHANNEL_PROVIDER_DELIVERY_READ_TIMEOUT_MS=5000`
- 검증:

```bash
tools/test/with-resource-lock.sh back-notification-provider \
  ./back/gradlew -p back test --tests '*NotificationChannelProvider*' --tests '*WebhookNotificationChannelProviderTest'
```

- bounded smoke:

```bash
tools/test/with-resource-lock.sh back-notification-provider-smoke \
  tools/test/run-notification-provider-delivery-smoke.sh
```

- smoke fixture는 local webhook server에서 `EMAIL=202 Accepted`, `SMS=delayed response`를 주입합니다.
- smoke 기대값은 `EMAIL -> SENT`, `SMS -> FAILED + nextAttemptAt=base+5s` 입니다.
- smoke가 실패하면 channel URL, timeout env, worker retry base delay drift를 먼저 확인합니다.
- skip가 늘면 `aquila_provider_delivery_skip_reason_count`의 reason별 증가와 verified contact 누락, inactive user, channel URL 오구성을 먼저 확인합니다.
- retry가 늘면 provider timeout과 응답 코드, `last_error`, `available_at` backoff 증가를 같이 봅니다.

### Nginx Reverse Proxy Baseline

- template 기준 파일: [ops/nginx/nginx.conf](/Users/aquila/Custom/GitProjects/aquila-bank/ops/nginx/nginx.conf)
- runtime env 예시: [ops/nginx/runtime.env.example](/Users/aquila/Custom/GitProjects/aquila-bank/ops/nginx/runtime.env.example)
- render script: `bash tools/ops/render-nginx-runtime-config.sh /tmp/aquila-bank-nginx.conf ops/nginx/runtime.env.example`
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
bash tools/test/run-nginx-runtime-template-gate.sh
```

- backend가 이미 `X-Accel-Buffering: no` 헤더를 응답하므로 Nginx도 buffering off 상태를 같이 유지합니다.
- `check-nginx-sse-proxy.sh`는 template directive drift만 확인하고, runtime render + `nginx -t`는 `run-nginx-runtime-template-gate.sh`에서 따로 검사합니다.
- `NOTIFICATION_SSE_CONNECTION_TIMEOUT_MS` 또는 upstream 포트를 바꾸면 Nginx timeout/upstream도 같이 맞춥니다.
- multi-node drain/reconnect runbook smoke는 아래 엔트리포인트를 사용합니다.

```bash
tools/test/run-sse-multinode-drain-smoke.sh
```

- 위 script는 `check-nginx-sse-proxy.sh`, reconnect storm replay, `NotificationSseBrokerTest`를 한 runbook smoke로 묶습니다.
- 기본 drain grace는 `5s`, reconnect delay baseline은 `3000ms` 입니다.
- scheduled workflow `SSE Multi-Node Drain Smoke`는 매주 목요일 03:30 KST(`30 18 * * 3` UTC)와 manual `workflow_dispatch`를 지원합니다.
- PR workflow `Nginx Runtime Gate`는 render + `nginx -t` strict gate를 수행합니다.
- repository variable로 `SSE_MULTINODE_DRAIN_GRACE_SECONDS`, `SSE_RECONNECT_DELAY_MS`를 override 할 수 있습니다.

## Transfer Reversal

기존 BOOKED 송금은 직접 수정하지 않고 reversal transaction을 추가해 취소/정정합니다.

- 공개 endpoint:
  - `POST /api/v1/transfers`
  - `POST /api/v1/transfers/{transactionReference}/reversal`
- reversal 요청 필드:
  - `sourceAccountId`
  - `amountMinor` (선택, 없으면 남은 reversal 가능 금액 전체)
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
  - 누적 reversal 금액이 원본 금액을 넘으면 `409 reversal amount exceeds remaining amount`
  - 이미 전액 reversal 된 원본 transfer를 다시 reversal 하면 `409 transfer is already reversed`
  - reversal 시 target 계좌 잔액이 부족하면 `409 reversal target balance is not enough`
- 운영 주의:
  - partial reversal은 원본 ledger row를 수정하지 않고 reversal ledger row를 누적 append 한다
  - 원본 read model 상태는 부분 reversal 후 `PARTIALLY_REVERSED`, 전액 누적 reversal 후 `REVERSED` 로 전이한다
  - notification inbox consumer는 `TransferBooked`, `TransferReversed` fan-out을 모두 처리하고 DLQ/ops 집계는 두 topic 합산 기준으로 본다

## Public Login Protection

공개 login 경로 `/api/v1/auth/login`에는 brute-force 1차 방어 기준이 기본 적용됩니다.

- 기본값:
  - `SECURITY_LOGIN_PROTECTION_MAX_FAILURES=5`
  - `SECURITY_LOGIN_PROTECTION_LOCK_SECONDS=900`
  - `SECURITY_LOGIN_PROTECTION_RESET_WINDOW_SECONDS=900`
  - `SECURITY_LOGIN_THROTTLING_IP_MAX_ATTEMPTS=20`
  - `SECURITY_LOGIN_THROTTLING_IP_WINDOW_SECONDS=60`
  - `SECURITY_LOGIN_THROTTLING_GLOBAL_MAX_ATTEMPTS=40`
  - `SECURITY_LOGIN_THROTTLING_GLOBAL_WINDOW_SECONDS=10`
  - `SECURITY_LOGIN_THROTTLING_MAX_TRACKED_IPS=1024`
  - `SECURITY_LOGIN_THROTTLING_STORE=memory`
  - `SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS=false`
  - `SECURITY_LOGIN_THROTTLING_REDIS_KEY_PREFIX=auth:login:throttle:`
- 동작 기준:
  - 같은 `loginId`에서 연속 `5회` 실패하면 `15분` 임시 잠금
  - 마지막 실패 후 `15분`이 지나면 실패 카운트는 다시 `1`부터 계산
  - 같은 IP에서 `1분` 동안 `20회`를 넘기면 `429 too many login attempts`로 fail-fast
  - 단일 app instance 기준 전체 login 시도가 `10초` 동안 `40회`를 넘기면 동일하게 `429`로 shed
  - 성공 login 시 `failed_login_count`, `last_login_failed_at`, `login_locked_until`은 reset
  - `loginId` 잠금은 존재 여부/잠금 여부를 드러내지 않도록 계속 `401 login failed` 유지
  - request-level throttling은 `Retry-After` 헤더와 함께 `429 too many login attempts`를 반환
  - memory 저장소의 IP/global throttling은 per-instance 기준이며 multi-node 전체 합산 limit는 보장하지 않음
  - Redis 저장소는 multi-node counter 공유가 필요할 때 사용하고, `require-redis=true` 운영값에서는 memory fallback 없이 설정 오류를 부팅 단계에서 드러냄
- Redis opt-in:
  - `SECURITY_LOGIN_THROTTLING_STORE=redis`일 때만 Redis-backed counter를 사용
  - multi-node 운영 baseline은 `SECURITY_LOGIN_THROTTLING_STORE=redis`와 `SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS=true`를 같이 둠
  - `SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS=true`인데 store가 `memory`이면 startup 단계에서 실패시켜 per-instance limit 오적용을 막음
  - Redis 연결값은 `REDIS_HOST`, `REDIS_PORT`로 지정
  - Redis runtime smoke는 compose Redis profile을 띄운 뒤 실제 counter 공유와 TTL 만료를 확인
  - 실행:
    ```bash
    tools/test/with-resource-lock.sh back-redis-login-throttling-runtime \
      tools/test/run-redis-login-throttling-runtime-smoke.sh
    ```
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
  - `POST /api/v1/auth/password-reset`
  - `GET /api/v1/auth/sessions`
  - `DELETE /api/v1/auth/sessions/{sessionId}`
  - `DELETE /api/v1/auth/sessions`
  - `POST /api/v1/auth/refresh`
  - `POST /api/v1/auth/logout`
- 응답 필드:
  - `status`
  - `accessToken`
  - `refreshToken`
  - `tokenType`
  - `expiresAt`
  - `refreshExpiresAt`
  - `userId`
- cookie 계약:
  - token pair를 발급하는 `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/mfa/totp/challenge/verify`, `POST /api/v1/auth/mfa/backup-codes/challenge/verify` 성공 응답은 `ab_refresh_device` HttpOnly cookie를 함께 내려준다
  - binding cookie는 `Secure`, `SameSite=Lax`, `Path=/api/v1/auth/refresh` 로 고정한다
  - request body에는 binding token을 추가하지 않고 refresh token body + cookie 조합만 유지한다
- 기본값:
  - `SECURITY_JWT_ACCESS_TOKEN_TTL_SECONDS=900`
  - `SECURITY_JWT_REFRESH_TOKEN_TTL_SECONDS=1209600`
  - `SECURITY_JWT_REFRESH_DEVICE_COOKIE_NAME=ab_refresh_device`
  - `AUTH_REFRESH_TOKEN_SESSION_CLEANUP_ENABLED=true`
  - `AUTH_REFRESH_TOKEN_SESSION_CLEANUP_RETENTION_DAYS=30`
  - `AUTH_REFRESH_TOKEN_SESSION_CLEANUP_BATCH_SIZE=500`
- 저장 기준:
  - raw refresh token은 응답으로만 한 번 내려가고 DB에는 `SHA-256 token_hash`만 저장
  - raw binding token도 cookie로만 한 번 내려가고 DB에는 `SHA-256 device_binding_hash`만 저장
  - 저장 테이블은 `auth_refresh_token_session`
  - access token JWT는 `user_id`와 함께 현재 refresh token session의 `session_id` claim도 포함한다
  - login/refresh 시 session row에 `device_name`, `ip_address`를 함께 저장
  - `device_name`은 request `User-Agent`를 경량 규칙으로 정리한 `OS / Browser` 값 우선 사용
  - `ip_address`는 `X-Forwarded-For` 첫 값, `Forwarded for=`, `X-Real-IP`, `remoteAddr` 순서로 해석
  - 성공 refresh 시 기존 row는 `ROTATED`, 새 row는 새 `token_hash`, 새 `device_binding_hash`, 현재 `device_name`, `ip_address`로 다시 저장된다
  - 성공 logout 시 현재 사용자 `ACTIVE` session은 `REVOKED`
  - cleanup batch는 `ACTIVE`는 `expires_at`, `ROTATED|REVOKED`는 `updated_at` 기준으로 retention cutoff 밖 row만 작은 batch로 삭제
- refresh 재발급 기준:
  - `POST /api/v1/auth/refresh` 는 request body `refreshToken`과 `ab_refresh_device` cookie가 모두 맞을 때만 성공한다
  - refresh 성공 시 refresh token과 binding cookie가 함께 rotate 되고 이전 refresh token, 이전 binding cookie는 모두 다시 쓸 수 없다
  - binding cookie가 없거나 mismatch이거나 기존 row의 `device_binding_hash`가 비어 있으면 generic `401 refresh failed` 로 거절한다
- 세션 목록 조회 기준:
  - `GET /api/v1/auth/sessions`
  - 현재 JWT user만 호출 가능하고 bootstrap account principal은 `403`
  - query parameter `size`는 기본 `20`, 최대 `50`
  - 응답은 현재 user의 `ACTIVE` 이면서 아직 만료되지 않은 session만 `expires_at DESC, id DESC` 순서로 반환
  - item 필드는 `sessionId`, `sessionStatus`, `expiresAt`, `lastUsedAt`, `createdAt`, `deviceName`, `ipAddress`, `currentSession`
  - `currentSession=true` 기준은 현재 access token의 `session_id` claim과 item `sessionId` 일치 여부다
  - deploy 직후 TTL 내에 남는 구 access token처럼 `session_id` claim이 없으면 인증은 유지하고 `currentSession`은 전부 `false`다
- 세션 종료 기준:
  - `DELETE /api/v1/auth/sessions/{sessionId}`는 현재 user 소유의 `ACTIVE` session 하나만 `REVOKED`로 바꾼다.
  - `DELETE /api/v1/auth/sessions`는 현재 user의 `ACTIVE` session 전체를 `REVOKED`로 바꾸고 `ab_refresh_device` cookie도 clear 한다.
  - 두 endpoint 모두 다른 사용자 session, 이미 `ROTATED|REVOKED` 상태, 존재하지 않는 session에 대해 `204` no-op을 유지한다.
- 비밀번호 재설정 기준:
  - `POST /api/v1/auth/password-reset`
  - 현재 JWT user만 호출 가능하고 bootstrap account principal은 `403`
  - request body는 `currentPassword`, `newPassword`
  - `currentPassword`가 맞고 `user_status = ACTIVE`일 때만 비밀번호를 새 `BCrypt password_hash`로 교체한다.
  - 성공 시 현재 user의 `ACTIVE` refresh session 전체를 `REVOKED`로 바꾸고 `ab_refresh_device` cookie도 clear 한다.
- forgot-password 복구 기준:
  - `POST /api/v1/auth/password-recovery/request`
  - 인증 없이 `loginId`만 받고 항상 `204 No Content`를 반환한다.
  - 응답에는 trace용 `X-Request-Id`와 별도로 internal handoff용 `X-Password-Recovery-Request-Id`가 내려간다.
  - active user가 있으면 같은 user의 기존 `PENDING` recovery token을 `SUPERSEDED`로 바꾸고 새 token을 발급한다.
  - 같은 user의 verified contact가 있으면 token 발급과 같은 transaction 안에서 `auth_password_recovery_delivery_outbox` row를 함께 적재하고, 외부 provider 호출은 worker가 비동기로 수행한다.
  - verified contact는 `EMAIL`을 우선 사용하고 없으면 `SMS`를 fallback으로 사용한다.
  - verified contact가 없으면 public 응답은 동일하게 `204 No Content`를 유지하되 token/outbox row를 만들지 않는다.
  - `auth_password_recovery_delivery_outbox.delivery_channel`과 `provider_destination`은 요청 시점 verified contact snapshot이다. 이후 contact가 바뀌어도 이미 적재된 recovery delivery 대상은 바꾸지 않는다.
  - 기본 provider adapter는 no-op이고, worker는 queue를 비운 뒤 row를 `SENT`로 정리한다.
  - `AUTH_PASSWORD_RECOVERY_DELIVERY_ENABLED=true`이고 provider URL이 없으면 logging adapter가 requestId/userId/expiresAt metadata만 기록하고 token 원문은 기록하지 않는다.
  - `AUTH_PASSWORD_RECOVERY_DELIVERY_ENABLED=true`이고
    `AUTH_PASSWORD_RECOVERY_DELIVERY_EMAIL_URL` 또는 `AUTH_PASSWORD_RECOVERY_DELIVERY_SMS_URL`가 있으면 webhook adapter가 JSON payload를 실제 provider endpoint로 `POST` 한다.
  - 해당 channel URL이 비어 있으면 잘못된 대상 전송 대신 delivery를 `SKIPPED`와 `skip_reason=PROVIDER_URL_MISSING`으로 정리하고 token 발급 결과는 유지한다.
  - webhook 요청 공통 header는 `AUTH_PASSWORD_RECOVERY_DELIVERY_AUTH_HEADER_NAME`, `AUTH_PASSWORD_RECOVERY_DELIVERY_AUTH_HEADER_VALUE`로 주입하고, `AUTH_PASSWORD_RECOVERY_DELIVERY_IDEMPOTENCY_HEADER_NAME` header에는 항상 `requestId`를 넣는다.
  - timeout은 `AUTH_PASSWORD_RECOVERY_DELIVERY_CONNECT_TIMEOUT_MS`, `AUTH_PASSWORD_RECOVERY_DELIVERY_READ_TIMEOUT_MS`로 조정하고, worker retry는 `AUTH_PASSWORD_RECOVERY_DELIVERY_WORKER_*` 설정으로 제어한다.
  - webhook payload는 `channel`, `requestId`, `userId`, `destination`, `recoveryToken`, `expiresAt`, `issuedAt` 필드를 포함한다.
  - worker는 작은 batch로 due row를 claim 하고 bounded exponential backoff 뒤 재시도하며, `AUTH_PASSWORD_RECOVERY_DELIVERY_WORKER_MAX_RETRY_ATTEMPTS` 도달 시 `QUARANTINED`로 격리한다.
  - 이미 `USED|EXPIRED|SUPERSEDED` 된 token 또는 내부 조회에서 사라진 token은 provider로 보내지 않고 `SKIPPED`와 token 계열 `skip_reason`으로 정리한다.
  - `POST /api/v1/auth/password-recovery/confirm`
  - 인증 없이 `recoveryToken`, `newPassword`를 받고 성공 시 `204 No Content`
  - wrong/expired/used token, `user_status != ACTIVE`는 모두 `401 password recovery failed`
  - recovery token 값은 public 응답에 직접 노출하지 않고
    `GET /internal/api/v1/auth/password-recovery-tokens/by-request-id?requestId=...` 에서만 확인한다.
  - internal lookup은 `internal:auth-admin` scope가 필요하고, missing/blank `requestId`는 `400`, unknown `requestId`는 `404`
  - forgot-password confirm 성공도 기존 self-service reset과 동일하게 현재 user의 `ACTIVE` refresh session 전체를 `REVOKED`로 바꾼다.
  - delivery ordering 보장은 `available_at, id` 기준 queue 순서까지만 두고, provider 중복 방지는 `requestId` idempotency key 계약에 맡긴다.
  - recovery token cleanup batch는 `PENDING`은 `expires_at`, `USED|EXPIRED|SUPERSEDED`는 `updated_at` 기준으로 retention cutoff 밖 row만 작은 batch로 삭제한다.
  - 기본 설정은 `AUTH_PASSWORD_RECOVERY_TOKEN_CLEANUP_ENABLED=true`, `AUTH_PASSWORD_RECOVERY_TOKEN_CLEANUP_RETENTION_DAYS=7`, `AUTH_PASSWORD_RECOVERY_TOKEN_CLEANUP_BATCH_SIZE=500` 이다.
- 거절 기준:
  - 만료, 이미 rotation 된 token, 존재하지 않는 token은 모두 `401 refresh failed`
  - `user_status=LOCKED|DISABLED` 사용자는 refresh로 새 token pair를 발급받지 못함
  - wrong `currentPassword` 또는 `user_status != ACTIVE`는 `401 password reset failed`
- 운영 주의:
  - logout은 access token 즉시 폐기가 아니라 refresh 재발급 차단까지만 처리
  - password reset도 access token 즉시 폐기가 아니라 refresh 재발급 차단까지만 처리
  - logout 성공 시 `ab_refresh_device` cookie를 clear 하고, 다른 사용자 token, 이미 `ROTATED|REVOKED` 상태인 token, 존재하지 않는 token으로 요청해도 응답은 `204` no-op을 유지한다
  - 선택 revoke와 전체 revoke도 access token 즉시 폐기가 아니라 refresh 재발급 차단까지만 처리
  - raw refresh token, raw binding token, plaintext secret은 로그/DB에 남기지 않음

## TOTP MFA

TOTP MFA는 `login -> challenge -> verify` 2단계 경로로만 token pair를 발급합니다.

- 공개 endpoint:
  - `POST /api/v1/auth/mfa/totp/enroll`
  - `POST /api/v1/auth/mfa/totp/enroll/verify`
  - `POST /api/v1/auth/mfa/totp/disable`
  - `POST /api/v1/auth/mfa/backup-codes`
  - `POST /api/v1/auth/mfa/backup-codes/challenge/verify`
  - `POST /api/v1/auth/mfa/totp/challenge/verify`
- 등록 기준:
  - enrollment start/verify는 현재 JWT user만 호출 가능하고 bootstrap account principal은 `403`
  - start 응답은 `status=PENDING`, `secretKey`, `otpauthUri`, `expiresAt`
  - verify request body는 `totpCode`
  - verify 성공 시 credential 상태는 `ACTIVE`, 응답은 `status=ACTIVE`, `verifiedAt`
- 해제 기준:
  - disable request body는 `totpCode`
  - 현재 JWT user와 현재 `ACTIVE` credential, 유효한 현재 TOTP code가 모두 맞을 때만 `204 No Content`
  - disable 성공 시 `auth_totp_credential` row는 삭제되고 해당 user의 active refresh session은 전부 `REVOKED`, `ab_refresh_device` cookie도 clear 된다
  - disable 성공 시 active backup code도 전부 `SUPERSEDED` 처리돼 이전 복구 수단이 남지 않는다
  - wrong code, 활성 credential 없음, 비활성 user는 `401 mfa disable failed`
- backup code 발급 기준:
  - request body는 `totpCode`
  - 발급/재발급은 현재 JWT user, 현재 `ACTIVE` TOTP credential, 유효한 현재 TOTP code가 모두 맞을 때만 성공한다
  - 응답은 `codeCount`, `backupCodes[]`이고 plain backup code는 이 응답에서만 1회 노출된다
  - 재발급 시 기존 active backup code는 전부 `SUPERSEDED`로 바뀌고 새 묶음만 `ACTIVE`로 남는다
  - wrong code, 활성 credential 없음, 비활성 user는 `401 backup code issue failed`
- 로그인 challenge 기준:
  - `ACTIVE` TOTP credential이 있는 사용자의 `POST /api/v1/auth/login` 응답은 `status=MFA_REQUIRED`
  - 이때 `challengeId`, `challengeType=TOTP`, `challengeExpiresAt`만 내려가고 token pair는 비어 있다
  - challenge verify 성공 시에만 `status=SUCCESS`와 token pair가 발급되고 `ab_refresh_device` cookie도 함께 내려간다
  - challenge verify는 `totpCode` 또는 `backupCode` 중 하나를 사용하며 backup code 성공 시 해당 row는 즉시 `USED` 처리된다
  - challenge verify는 로그인 시점의 `device_name`, `ip_address` 메타데이터를 그대로 session row에 저장한다
- remember device 기준:
  - TOTP/backup code challenge verify request body는 선택 필드 `rememberDevice=true` 를 받을 수 있다
  - `rememberDevice=true` 로 challenge verify 성공 시 응답 body가 아니라 `Set-Cookie: ab_mfa_remember_device=...` 로만 raw token을 내려준다
  - cookie는 `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, `Max-Age=2592000(30일)` 기준을 사용한다
  - active TOTP credential이 있는 사용자도 유효한 remember device cookie가 있으면 다음 `POST /api/v1/auth/login` 에서 challenge 없이 바로 token pair를 받는다
  - remember device bypass 성공 시 token은 즉시 rotate 되고 새 cookie로 다시 내려가며, 만료/무효/stale cookie는 MFA bypass 없이 `MFA_REQUIRED` 로 돌아가고 응답에서 clear 된다
  - 현재 cookie logout, `DELETE /api/v1/auth/sessions`, `POST /api/v1/auth/password-reset`, `POST /api/v1/auth/mfa/totp/disable` 성공 시 remember device도 함께 revoke 되고 cookie도 clear 된다
- 기본값:
  - `SECURITY_TOTP_ISSUER=Aquila Bank`
  - `SECURITY_TOTP_SECRET_ENCRYPTION_KEY` 필수
  - `SECURITY_TOTP_ENROLLMENT_TTL_SECONDS=300`
  - `SECURITY_TOTP_CHALLENGE_TTL_SECONDS=300`
  - `SECURITY_TOTP_CHALLENGE_MAX_ATTEMPTS=5`
- 저장 기준:
  - TOTP secret raw/base32 값은 응답으로만 내려가고 DB에는 AES-GCM 보호 값만 저장
  - credential 테이블은 `auth_totp_credential`
  - login challenge 테이블은 `auth_totp_login_challenge`
  - backup code 테이블은 `auth_mfa_backup_code`
  - remember device 테이블은 `auth_mfa_remember_device`
  - backup code는 plain 값이나 복호화 가능한 ciphertext 없이 `SHA-256` hash만 저장한다
  - remember device도 raw token 없이 `SHA-256` hash만 저장하고, lookup은 `user_id + token_hash` exact match로만 수행한다
  - challenge row는 `user_id` 기준 1행만 유지해 user별 현재 pending state만 덮어쓴다
- 거절 기준:
  - wrong TOTP code, 만료 challenge, 사용 완료 challenge는 `401 mfa challenge failed`
  - wrong backup code, 이미 `USED|SUPERSEDED` 된 backup code도 `401 mfa challenge failed`
  - challenge 시도 횟수가 상한에 도달하면 상태를 `FAILED`로 바꾸고 같은 challenge는 더 이상 성공하지 못한다
  - pending enrollment가 없거나 이미 만료된 enrollment verify는 `400`

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

### 404/409/500 후속 운영 기준

| 상태 | 기본 처리 | 승격 트리거 | 우선 확인 |
| --- | --- | --- | --- |
| `404 Not Found` | 단발 오호출/대상 불일치 후보로 먼저 분류 | 같은 `actorSubject + path` 반복, 여러 target miss 확산 | `targetUserId`/`targetAccountId` 식별자 drift |
| `409 Conflict` | 단발 중복 호출/재시도 충돌 후보로 먼저 분류 | 같은 `actorSubject + path + requestedStatus` 반복, 다른 actor 간 충돌 | success audit row 인접 존재 여부 |
| `500 Internal Server Error` | 단건이어도 incident 후보로 즉시 triage | 같은 시간대 반복 또는 여러 actor/path 확산 | `requestId` 기준 로그 타임라인과 서버 측 오류 확산 |

아래 상세 섹션에서 `수집 패턴`, `제외/승격 조건`, `requestId` 추적 차이를 status별로 이어 봅니다.

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
  - 내부 auth 409 응답은 기존 `message`를 유지하면서 분기용 `reasonCode`를 함께 반환합니다.
  - 현재 표준 code는 `DUPLICATE_LOGIN_ID`, `DUPLICATE_EXTERNAL_IDENTITY_MAPPING`, `STATUS_TRANSITION_CONFLICT`입니다.
  - `STATUS_TRANSITION_CONFLICT`는 상태 전이 불가 예외가 추가될 때 쓰는 예약 code이며, 현재 대표 실사용 code는 중복 loginId와 external identity mapping 중복입니다.
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
- 응답 예시:

```json
{
  "status": 409,
  "error": "Conflict",
  "reasonCode": "DUPLICATE_EXTERNAL_IDENTITY_MAPPING",
  "message": "external identity mapping already exists"
}
```

- rollback:
  - PR revert 시 `reasonCode` 필드만 사라지고 기존 `message` 기반 fallback은 유지됩니다.
  - 운영 스크립트는 배포 전환 기간 동안 `reasonCode` 우선, 없으면 `message` fallback 순서로 분기합니다.

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

- `404`, `409`는 단발 실패를 바로 alert로 승격하지 않고 대상 drift, 중복 호출, 재시도 충돌을 먼저 분리합니다.
- `500`은 제외보다 incident triage가 우선이므로 단건이어도 즉시 조사 대상으로 둡니다.

#### 404 운영 기준

- 기본 처리:
  - 단발 `404`는 warning 전송보다 stale target 또는 수동 오입력 여부를 먼저 확인합니다.
- 제외 조건:
  - 단발 `404`이고 같은 actor가 직후 대상 식별자를 수정해 성공 호출로 전환한 경우
  - 수동 점검 과정에서 잘못된 `userId`/`accountId`를 한 번 입력한 경우
- 승격 조건:
  - 같은 `actorSubject + path`에서 동일 target miss가 `10분 내 3회 이상` 반복되면 warning 후보
  - 같은 배치 또는 actor가 여러 target에서 `404`를 확산시키면 critical 후보

#### 409 운영 기준

- 기본 처리:
  - 단발 `409`는 warning 전송보다 중복 호출, caller retry, 상태 전이 충돌 후보를 먼저 분리합니다.
  - 응답 `reasonCode`를 우선 확인하고, 필드가 없으면 구버전 응답으로 보고 `message` fallback을 사용합니다.
- 제외 조건:
  - 같은 actor의 단발 중복 호출이고, 인접 시간대에 성공 감사 row가 확인되는 경우
  - 수동 재실행이나 caller retry가 이미 적용된 상태로 보이는 경우
- 승격 조건:
  - 같은 `actorSubject + path + requestedStatus` 조합의 `409`가 `10분 내 3회 이상` 반복되고 success audit row가 확인되지 않으면 warning 후보
  - 서로 다른 actor가 같은 target에 충돌하는 정황이 보이면 critical 후보

#### 500 운영 기준

- 기본 처리:
  - 단발 `500`도 caller 오입력보다 서버 측 장애 후보로 먼저 triage 합니다.
- 제외 조건:
  - 기본적으로 제외하지 않습니다.
- 승격 조건:
  - `500` 한 건만으로도 incident 후보로 triage 합니다.
  - 같은 시간대에 `500`이 2회 이상 반복되거나 여러 actor/path로 확산되면 critical 후보로 봅니다.

### 404/409/500 requestId 추적 차이와 triage 순서

- `404`, `409`는 `requestId`를 찾은 뒤 대상 식별자 drift 또는 중복 호출 정황을 먼저 분리합니다.
- `500`은 `requestId`를 찾는 즉시 같은 시간대 확산 여부와 서버 측 오류 타임라인을 먼저 확인합니다.

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

success audit 목록/검색 예시:

```bash
curl -sS \
  -H "Authorization: Bearer $AUTH_ADMIN_SERVICE_TOKEN" \
  "http://localhost:8080/internal/api/v1/auth/status-change-audits?fromCreatedAt=2026-04-01T00:00:00Z&toCreatedAt=2026-04-22T00:00:00Z&targetUserId=21&targetAccountId=101&changeType=MEMBERSHIP_STATUS&reasonCode=OPS_MANUAL&size=50"
```

- 목록 API는 `created_at DESC, id DESC` keyset pagination만 사용합니다. 다음 페이지는 응답 `nextCursor`를 `cursor` query로 그대로 전달합니다.
- 지원 필터는 `fromCreatedAt`, `toCreatedAt`, `targetUserId`, `targetAccountId`, `changeType`, `reasonCode`, `size`, `cursor`입니다.
- 기본 `size=50`, 최대 `size=200`이며 그 이상은 서버에서 `200`으로 제한합니다.
- 검색 index는 `idx_auth_status_change_audit_*_created_id` 계열로 유지합니다. rollback 시 API PR revert와 함께 `V42__add_auth_status_change_audit_search_indexes.sql`로 추가된 index 제거 여부를 확인합니다.

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
OUTBOX_POLLER_MAX_RETRY_ATTEMPTS=10
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
NOTIFICATION_INBOX_CONSUMER_CONCURRENCY=1
NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED=true
NOTIFICATION_INBOX_CONSUMER_OPS_HEALTH_MAX_LAG_MESSAGES=100
NOTIFICATION_INBOX_CONSUMER_OPS_HEALTH_MAX_DLQ_COUNT=0
KAFKA_TOPIC_PROVISIONING_PARTITIONS=1
KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR=1
KAFKA_TOPIC_PROVISIONING_MIN_IN_SYNC_REPLICAS=1
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
- `quarantinedCount` 는 자동 retry 에서 분리된 poison row 수다. health 임계값에는 직접 연결하지 않고 metric/summary 로 수동 triage 한다.

### 기본 triage 순서

1. `/actuator/health` 가 `503`이면 `/internal/api/v1/outbox/summary` 를 먼저 조회해 `lagSeconds`, `failedCount`, `producerTimeoutFailedCount`, `staleSendingCount` 중 초과 축을 확인합니다.
2. `producerTimeoutFailedCount` 또는 `failedCount` 가 크면 `tools/ops/outbox-find-failed-events.sh` 로 bounded failed list 를 보고 `eventKey`, `retryCount`, `lastError` 를 먼저 확인합니다.
3. `quarantinedCount` 가 0보다 크면 같은 오류의 반복 실패가 최대 retry attempt 에 도달한 상태이므로 DB row의 `eventKey`, `eventType`, `lastError`, `payload` 를 확인하고 payload/topic/reference data 수정 필요 여부를 먼저 판단합니다.
4. `/internal/api/v1/outbox/notification/summary` 또는 `tools/ops/notification-get-consumer-summary.sh` 로 consumer lag 와 DLQ count 를 확인합니다.
5. `dlqCount` 가 0보다 크면 `tools/ops/notification-find-dlq-events.sh` 로 poison message 최근 항목을 보고 `eventKey`, `partition`, `offset`, `originalTopic`, `errorClass`, `errorMessage`, `payloadPreview` 를 먼저 확인합니다.
6. redrive 대상이 명확하면 `tools/ops/notification-redrive-dlq-event.sh` 로 preview -> redrive -> summary 순서로 한 건씩 재처리하고, 응답의 `targetTopic`, `targetPartition`, `targetOffset` 을 기록합니다.
7. `staleSendingCount` 가 0보다 크면 `tools/ops/outbox-recover-stale-sending.sh` 를 한 번만 호출하고, 응답의 `recoveredCount` 와 이후 summary 변화를 확인합니다.
8. recovery 이후에도 `lagSeconds`, `lagCount`, `failedCount` 가 계속 증가하면 producer timeout, consumer 중단, broker 연결 문제를 별도 incident 로 분리합니다.

### 운영 주의사항

- stale recovery 는 직접 publish 가 아니라 stale `SENDING` row 를 `PENDING` 으로 되돌리는 동작입니다.
- recovery 대상은 `outbox.poller.stale-after-seconds` 를 넘긴 row 만 포함합니다.
- failed list 는 `availableAt ASC, id ASC` 순서의 bounded query 이므로, 대량 backlog 에서도 즉시 재시도 대상부터 확인할 수 있습니다.
- `QUARANTINED` row 는 dispatch claim, failed list, stale recovery 에서 제외됩니다.
- `OUTBOX_POLLER_MAX_RETRY_ATTEMPTS` 는 실패 시도 횟수 기준입니다. 기본값 `10`에서는 10번째 실패 시 quarantine 으로 전환됩니다.
- quarantine row 는 payload/reference data 수정 없이 상태만 `PENDING` 으로 되돌리면 같은 실패를 반복할 수 있으므로 원인 확인 전 수동 복구하지 않습니다.
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
