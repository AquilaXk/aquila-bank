# transaction-100m-local-t3micro-20260425-bottleneck

## Summary

- 실행일: 2026-04-25 KST
- 환경: local Docker compose `compose.yml` + `compose.t3micro.yml` + `compose.loadtest.yml`
- 목표: transaction read model 1억 row 적재 후 k6 HTTP 부하 테스트로 병목 확인
- 결과: k6 HTTP 부하 단계까지 도달하지 못함
- 주 병목: PostgreSQL 384MiB 제한에서 5천만 row 단일 btree secondary index build가 OOM을 유발

## Command

```bash
K6_REPORT_NAME=transaction-100m-local-t3micro-20260425-8vu-1m \
K6_VUS=8 \
K6_DURATION=1m \
SEED_TOTAL_ROWS=100000000 \
SEED_BATCH_SIZE=1000000 \
SEED_TRUNCATE=true \
tools/test/run-transaction-read-model-100m-k6-local.sh
```

## Dataset State

- seed 설정: hot 50,000,000 rows + archive 50,000,000 rows
- insert log 기준 hot/archive batch는 모두 완료됨
- PK 범위 확인:

```text
transaction_read_model         min_id=1        max_id=50000000
transaction_read_model_archive min_id=50000001 max_id=100000000
```

- crash 이후 secondary read index 상태:

```text
transaction_read_model         transaction_read_model_pkey
transaction_read_model         uq_transaction_read_model_ledger_entry
transaction_read_model_archive transaction_read_model_archive_pkey
transaction_read_model_archive uq_transaction_read_model_archive_ledger_entry
```

- 누락된 조회 index:

```text
idx_transaction_read_model_account_cursor
idx_transaction_read_model_account_status_cursor
idx_transaction_read_model_account_reference_cursor
idx_transaction_read_model_cleanup_cursor
idx_transaction_read_model_archive_account_cursor
idx_transaction_read_model_archive_account_status_cursor
idx_transaction_read_model_archive_account_reference_cursor
```

## Failure Evidence

### 1. secondary index 재생성 중 PostgreSQL OOM

- 위치: `seed-transaction-read-model-100m.sh`의 `create_secondary_indexes`
- 로그:

```text
2026-04-25 02:08:55.509 KST [1] LOG: background writer process (PID 74) was terminated by signal 9: Killed
2026-04-25 02:08:55.510 KST [1] LOG: terminating any other active server processes
```

- Docker state:

```text
OOMKilled=true
```

### 2. exact count 확인 쿼리도 OOM 유발

- 실행 쿼리:

```sql
SELECT 'transaction_read_model' AS table_name, count(*) AS rows, min(id) AS min_id, max(id) AS max_id
FROM transaction_read_model
UNION ALL
SELECT 'transaction_read_model_archive', count(*), min(id), max(id)
FROM transaction_read_model_archive;
```

- 로그:

```text
2026-04-25 02:09:49.424 KST [1] LOG: background worker "parallel worker" (PID 2538) was terminated by signal 9: Killed
2026-04-25 02:09:49.424 KST [1] DETAIL: Failed process was running: SELECT ... count(*) ...
```

### 3. 최소 index 1개도 low-memory/no-parallel 조건에서 OOM

- 실행 쿼리:

```sql
SET maintenance_work_mem='16MB';
SET max_parallel_maintenance_workers=0;
CREATE INDEX IF NOT EXISTS idx_transaction_read_model_account_cursor
  ON transaction_read_model (account_id, booked_at DESC, id DESC);
```

- 로그:

```text
2026-04-25 02:10:38.442 KST [1] LOG: checkpointer process (PID 30) was terminated by signal 9: Killed
2026-04-25 02:10:38.442 KST [1] LOG: terminating any other active server processes
```

## Query Plan Without Secondary Index

secondary index가 없는 상태에서 hot account 조회는 `Parallel Seq Scan + Sort`로 떨어진다.

```text
Limit
  -> Gather Merge
       Workers Planned: 2
       -> Sort
            Sort Key: booked_at DESC, id DESC
            -> Parallel Seq Scan on transaction_read_model
                 Filter: booked_at range + account_id
```

이 상태에서 k6를 실행하면 1억 row 조회 성능 검증이 아니라 index 부재에 따른 seq scan/timeout/OOM 검증이 되므로 중단했다.

## Resource Snapshots

### Before seed

```text
aquila-bank-backend-loadtest  416.1MiB / 1GiB
aquila-bank-postgres           94.7MiB / 384MiB
disk available                170Gi
```

### During insert

```text
aquila-bank-postgres CPU      약 55~64%
aquila-bank-postgres memory   약 208~244MiB / 384MiB
```

### After OOM recovery

```text
aquila-bank-postgres memory   약 43MiB / 384MiB
disk available                151Gi
```

### Relation size after failed index build

```text
transaction_read_model         8879 MB
transaction_read_model_archive 9656 MB
```

## Secondary Findings

- PostgreSQL logs에 checkpoint 빈발 경고가 반복됨.

```text
checkpoints are occurring too frequently (10 seconds apart)
HINT: Consider increasing the configuration parameter "max_wal_size".
```

- 현재 `max_wal_size`는 `1GB`.
- `postgres-exporter:v0.15.0`가 PostgreSQL 18의 `pg_stat_bgwriter` 컬럼과 맞지 않아 5초마다 error를 기록함.

```text
ERROR: column "checkpoints_timed" does not exist
STATEMENT: SELECT checkpoints_timed, checkpoints_req, ...
```

## Bottleneck Conclusion

1. t3.micro PostgreSQL 384MiB 제한에서 5천만 row 단일 테이블 btree index build는 OOM으로 실패한다.
2. secondary read index가 없으면 transaction read API는 5천만 row `Parallel Seq Scan + Sort` 계획으로 떨어져 k6 HTTP 부하 테스트를 진행할 수 없다.
3. 1억 row seed 자체보다 `index build`, `full scan`, `checkpoint/WAL pressure`가 먼저 터지는 병목이다.
4. 관찰 환경도 PostgreSQL 18과 exporter collector 호환성 문제가 있어 부하 중 DB 로그 노이즈가 크다.

## Next Actions

- 완료: local seed 기본값을 `SEED_INDEX_STRATEGY=required`로 바꿔 k6에 필요한 account cursor index를 빈 table 상태에서 유지한다.
- 완료: t3.micro loadtest PostgreSQL config에 `max_wal_size`, checkpoint, parallel worker, JIT, autovacuum 기준을 명시한다.
- 완료: k6 runner가 required read index 존재 여부와 Docker `OOMKilled` 상태를 preflight로 검사하고, index가 없으면 k6를 실행하지 않게 한다.
- 완료: PostgreSQL 18에서 깨지는 `postgres-exporter` `stat_bgwriter` collector를 local loadtest runtime에서 비활성화한다.
- 후속: transaction read model을 월/기간 기준 partition 또는 chunk 구조로 전환해 운영에서도 1억 row index build 폭탄이 생기지 않게 한다.
- 후속: partition lifecycle/runbook/retention automation을 추가한다.
