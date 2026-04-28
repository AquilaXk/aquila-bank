# Transaction Read Model Chunk Lifecycle Runbook

## Purpose

`transaction_read_model`과 `transaction_read_model_archive`는 `booked_at` 월별 partition parent입니다. 이 runbook은 운영에서 다음 달 chunk를 미리 만들고, 오래된 archive chunk를 detach/drop 후보로 분리하며, partition별 planner stats를 최신 상태로 유지하는 절차를 고정합니다.

## Principles

- hot table 삭제는 row-level retention을 먼저 사용합니다. hot partition detach는 이 PR 범위가 아닙니다.
- archive partition detach/drop은 월 경계가 지난 cold data에만 적용합니다.
- 모든 destructive 작업은 먼저 `--print-sql`로 review합니다.
- `drop-detached` 실행은 `CONFIRM_DROP=drop-detached-transaction-read-model`이 없으면 실패합니다.
- `lock_timeout` 기본값은 `1000ms`, `statement_timeout` 기본값은 `30000ms`로 두어 OCI A1 단일 노드 PostgreSQL에서 장기 lock을 만들지 않습니다.

## Monthly Precreate

다음 4개월 hot/archive partition을 미리 생성합니다. 신규 데이터는 이미 index가 붙은 작은 partition에 insert되어 사후 대형 btree build를 피합니다.

```bash
tools/ops/transaction-read-model-chunk-lifecycle.sh \
  --action precreate \
  --target both \
  --reference-month "$(date -u +%Y-%m)" \
  --months-ahead 4 \
  --print-sql
```

실행:

```bash
DATABASE_URL="$STAGING_RDS_DATABASE_URL" \
tools/ops/transaction-read-model-chunk-lifecycle.sh \
  --action precreate \
  --target both \
  --reference-month "$(date -u +%Y-%m)" \
  --months-ahead 4
```

권장 주기:

- 매월 25일 이후 1회
- 대량 bootstrap/import 전 1회
- staging 배포 후 1억 row replay 전 1회

## Retention Plan

오래된 archive partition 후보를 먼저 조회합니다. `before-month`는 해당 월 1일보다 `month_end`가 작거나 같은 partition만 후보로 봅니다.

```bash
tools/ops/transaction-read-model-chunk-lifecycle.sh \
  --action retention-plan \
  --target archive \
  --before-month 2025-10 \
  --print-sql
```

출력의 `recommended_action` 기준:

- `detach_archive_candidate`: archive partition detach 가능 후보
- `run_row_retention_before_hot_detach`: hot partition은 row retention 선행 필요
- `keep`: cutoff 밖이라 유지

## Detach Archive Chunk

detach는 archive parent만 지원합니다. detached table은 `transaction_read_model_detached` schema로 이동합니다.

```bash
DATABASE_URL="$STAGING_RDS_DATABASE_URL" \
tools/ops/transaction-read-model-chunk-lifecycle.sh \
  --action detach \
  --target archive \
  --before-month 2025-10
```

검증:

```sql
SELECT schemaname, tablename
FROM pg_tables
WHERE schemaname = 'transaction_read_model_detached'
ORDER BY tablename;
```

## Drop Detached Archive Chunk

drop은 detach 이후 별도 window에서 실행합니다. review 없이 실행되지 않도록 confirmation env가 필요합니다.

```bash
tools/ops/transaction-read-model-chunk-lifecycle.sh \
  --action drop-detached \
  --before-month 2025-10 \
  --print-sql
```

실행:

```bash
CONFIRM_DROP=drop-detached-transaction-read-model \
DATABASE_URL="$STAGING_RDS_DATABASE_URL" \
tools/ops/transaction-read-model-chunk-lifecycle.sh \
  --action drop-detached \
  --before-month 2025-10
```

## Partition Maintenance

planner stats는 parent만이 아니라 leaf partition 기준으로 갱신합니다.

```bash
DATABASE_URL="$STAGING_RDS_DATABASE_URL" \
tools/ops/transaction-read-model-chunk-lifecycle.sh \
  --action analyze \
  --target both
```

dead tuple이 많은 archive partition은 보수적으로 `vacuum-analyze`를 실행합니다.

```bash
DATABASE_URL="$STAGING_RDS_DATABASE_URL" \
tools/ops/transaction-read-model-chunk-lifecycle.sh \
  --action vacuum-analyze \
  --target archive
```

대상 parent table의 stale stats guard는 기존 script로 확인합니다.

```bash
DATABASE_URL="$STAGING_RDS_DATABASE_URL" \
tools/ops/transaction-read-model-planner-stats-freshness-guard.sh
```

## SLO Verification

OCI A1 cloud baseline loadtest:

```bash
SEED_TOTAL_ROWS=100000000 \
SEED_BATCH_SIZE=250000 \
SEED_TRUNCATE=true \
tools/test/run-transaction-read-model-100m-k6-local.sh
```

legacy 이름의 wrapper는 backend를 force-recreate해 최신 Flyway runtime을 보장하고, `flyway_schema_history` 최신 version을 migration 최신 version과 비교한 뒤 OCI A1 PostgreSQL seed를 시작합니다. 기본 conflict mode는 `fail`이라 truncate 신규 seed에서 `ON CONFLICT` 비용을 내지 않습니다.

이미 dataset이 준비되어 있으면 k6만 실행합니다.

```bash
K6_HOT_ACCOUNT_ID="$HOT_ACCOUNT_ID" \
K6_COLD_ACCOUNT_ID="$COLD_ACCOUNT_ID" \
K6_HOT_FROM="$HOT_FROM" \
K6_HOT_TO="$HOT_TO" \
K6_COLD_FROM="$COLD_FROM" \
K6_COLD_TO="$COLD_TO" \
tools/test/run-transaction-read-model-100m-k6-local.sh --k6-only
```

관측 지점:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`
- k6 summary: `build/reports/k6/*-summary.md`
- archived summary: `docs/performance-results/*`

localhost URL은 관측 stack을 같은 host 또는 SSH tunnel로 볼 때의 접속 예시입니다.

운영/staging replay:

권장 baseline은 OCI A1 Flex 4 OCPU / 24GB + data 300GB self-managed PostgreSQL 18입니다. 1억 건 dataset은 OCI A1 data volume에 적재하고, AWS EC2 staging smoke는 app 배포 확인 범위로만 사용합니다.

```bash
STAGING_BASE_URL="$STAGING_BASE_URL" \
STAGING_REPLAY_TOKEN="$STAGING_REPLAY_TOKEN" \
STAGING_RDS_DATABASE_URL="$STAGING_RDS_DATABASE_URL" \
HOT_ACCOUNT_ID="$HOT_ACCOUNT_ID" \
HOT_FROM="$HOT_FROM" \
HOT_TO="$HOT_TO" \
COLD_ACCOUNT_ID="$COLD_ACCOUNT_ID" \
COLD_FROM="$COLD_FROM" \
COLD_TO="$COLD_TO" \
tools/ops/transaction-read-model-staging-replay.sh
```

SLO 기준:

- hot first/cursor p95: 기본 `350ms` 이하
- cold first/cursor p95: 기본 `750ms` 이하
- `aquila_transaction_query_latency_seconds_bucket` 기반 p95 alert가 query shape별로 과도하게 치솟지 않아야 합니다.
- k6 `http_req_failed` rate는 `0.01` 이하를 유지합니다.

## Rollback

- precreate rollback: 생성된 future partition이 비어 있으면 수동 `DROP TABLE public.<partition_name>;`로 제거합니다.
- detach rollback: `transaction_read_model_detached` schema의 table을 원래 parent에 다시 attach해야 하므로, 같은 월 range와 row overlap을 먼저 확인합니다.
- drop rollback: table drop 이후에는 DB snapshot/PITR 복구가 필요합니다.
- script/doc rollback: PR revert로 수행합니다.
