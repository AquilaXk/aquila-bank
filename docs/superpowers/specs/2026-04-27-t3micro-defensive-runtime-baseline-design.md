# Small-Instance Defensive Runtime Baseline Design

## 목적

현재 프로젝트 목표를 "단일 t3.micro에서 초대량 트래픽과 1억 건 조회 처리"에서 "EC2 t3.small + RDS PostgreSQL 18.3 db.t4g.medium + gp3 150GiB에서 대용량 트래픽 방어와 bounded 1억 건 조회"로 재정의한다.

핵심은 처리량 확장이 아니라 작은 인프라가 과부하를 받을 때 빠르게 제한하고, DB/worker/SSE/API 동시성 비용을 예측 가능하게 유지하는 것이다.

## 범위

- README와 agent context의 목표/비목표 문구 갱신
- Kafka를 로컬 compose 기본 실행에서 제외하고 opt-in profile로 유지
- Prometheus/Grafana/Alertmanager는 상시 운영 필수 구성에서 제외하고 부하테스트/선택 운영 자산으로 명시
- production high-traffic config gate를 Kafka/read replica 필수값 중심에서 방어형 runtime budget 중심으로 변경
- worker batch, SSE 연결 수, API 동시성 제한, DB pool/server thread 기준을 작은 기본값으로 유지
- 거래 조회 목표를 `accountId + 기간 + keyset pagination` 경로로 제한

## 비범위

- Kafka adapter 코드와 테스트 전체 제거
- Prometheus/Grafana dashboard asset 삭제
- 거래/잔액/원장 write path 변경
- 전체 1억 건 검색/집계/정렬 기능 추가
- DB schema/index 신규 migration
- read replica 필수 운영 전제 유지

## 아키텍처 결정

### Kafka

Kafka는 outbox/notification 확장 adapter로 남기되 기본 runtime에서 비활성화한다. `OUTBOX_KAFKA_ENABLED=false`, `NOTIFICATION_INBOX_CONSUMER_ENABLED=false` 기본값은 유지한다. 로컬 compose의 Kafka service는 `kafka` profile로 옮겨 명시적으로 opt-in 할 때만 실행한다.

이 방식은 기존 notification/outbox 확장 경계를 보존하면서, small-instance 방어형 baseline에서 broker CPU/메모리 비용을 기본값으로 부담하지 않게 한다.

### Monitoring

Prometheus/Grafana/Alertmanager asset은 삭제하지 않는다. 다만 README와 ops README에서 "상시 운영 필수"가 아니라 import/apply 가능한 baseline 및 loadtest overlay임을 명확히 한다. 상시 운영은 EC2 t3.small app budget 밖의 별도 관측 환경 또는 필요 시 단기 실행으로 다룬다.

### Production Gate

production high-traffic config gate는 다음을 필수로 검증한다.

- login throttling은 Redis opt-in 또는 memory fallback 중 하나가 명확할 것
- API admission control 활성화
- transaction read concurrency 상한 유지
- small-instance saturation guard 활성화
- DB pool, server thread, SSE session cap, worker batch가 작은 상한 안에 있을 것
- Kafka/read replica는 enabled일 때만 세부 값 검증

### Transaction Query

거래 목록/아카이브 조회는 기존 구현처럼 `accountId`, `from`, `to`, `limit`, `cursor`를 기본 계약으로 유지한다. `TransactionReadQueryStatement`와 archive query는 `(booked_at, id)` keyset 조건과 `ORDER BY booked_at DESC, id DESC`를 맞춰 composite index range scan을 유도한다.

전체 1억 건 검색, 전체 집계, 전체 정렬은 목표에서 제외한다. 1억 건은 저장 규모와 분포를 뜻하고, 온라인 조회 단위는 계좌/기간으로 bounded된 page 조회만 허용한다.

## 운영 기본값

- `DB_POOL_MAX_SIZE`: 기본 4, production 방어 기준 상한 4
- `SERVER_THREADS_MAX`: 기본 16, production 방어 기준 상한 16
- `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX`: 기본 3
- `NOTIFICATION_SSE_MAX_TOTAL_SESSIONS`: 기본 64, production 방어 기준 상한 64
- outbox/provider/cleanup worker batch는 작은 batch를 유지하고 production gate에서 상한을 검증한다.

## 검증

- `tools/ops/validate-production-high-traffic-config.sh --print-plan`
- Kafka/read replica env 없이 방어형 필수값만 넣어 `tools/ops/validate-production-high-traffic-config.sh`
- Kafka/read replica를 enabled로 둔 경우 관련 필수 env 누락 시 실패하는 smoke
- `docker compose config --services`
- `docker compose --profile kafka config --services`
- `git diff --check`

## 리스크와 롤백

- 기존 Kafka 필수 운영을 전제로 만든 배포 환경은 gate 통과 기준이 바뀐다. Kafka를 계속 쓰는 환경은 `OUTBOX_KAFKA_ENABLED=true`, `NOTIFICATION_INBOX_CONSUMER_ENABLED=true`, `KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED=true`를 명시해 opt-in 검증을 받는다.
- Prometheus/Grafana를 같은 host에 상시 올려 두던 환경은 문서 목표와 달라진다. 필요하면 외부 관측 host 또는 단기 loadtest overlay로 분리한다.
- 롤백은 이 PR revert로 수행한다. Kafka service profile과 production gate 변경이 이전 기준으로 복원된다.
