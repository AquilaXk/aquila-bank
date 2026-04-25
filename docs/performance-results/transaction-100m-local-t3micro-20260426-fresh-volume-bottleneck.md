# transaction-100m-local-t3micro-20260426-fresh-volume-bottleneck

## Summary

- 실행일: 2026-04-26 KST
- Git 기준: `1b5ac31` (`main`, #359 재실행 리포트 포함)
- 환경: local Docker compose `compose.yml` + `compose.t3micro.yml` + `compose.loadtest.yml`
- 목표: fresh PostgreSQL volume에서 1억 row 거래 조회 seed 후 k6 HTTP 부하 테스트로 병목 확인
- 결과: k6 HTTP 부하 단계까지 도달하지 못함
- 주 병목: PostgreSQL 384MiB 제한에서 hot table 125만 row 배치 중 insert backend가 OOM kill

## Fresh Volume Setup

삭제 대상은 현재 compose project의 PostgreSQL data volume이다.

```text
removed volume: aquila-bank_aquila-bank-postgres-data
old createdAt: 2026-04-24T16:46:10Z
new createdAt: 2026-04-25T15:39:13Z
```

실행 중 fresh volume 생성 확인:

```text
Volume aquila-bank_aquila-bank-postgres-data Creating
Volume aquila-bank_aquila-bank-postgres-data Created
```

## Command

```bash
tools/test/run-transaction-read-model-100m-k6-local.sh --print-plan
tools/test/run-transaction-read-model-100m-k6-local.sh
```

실행 기본값:

```text
SEED_TOTAL_ROWS=100000000
SEED_BATCH_SIZE=250000
SEED_TRUNCATE=true
SEED_INDEX_STRATEGY=required
SEED_CONFLICT_MODE=fail
K6_VUS=8
K6_DURATION=1m
K6_LIMIT=50
```

## Environment

- PostgreSQL: `postgres:18`, cgroup memory `384MiB`, pids `128`
- Backend: `eclipse-temurin:21-jre`, cgroup memory `1GiB`
- Observability: Prometheus `9090`, Grafana `3001`, Alertmanager `9093`, postgres-exporter `9187`
- 실행 전 disk: `164GiB` available
- 실패 후 disk: `168GiB` available
- Flyway preflight: `applied=52 required=52`

## Failure Point

fresh volume에서 Flyway V52까지 적용된 뒤 seed가 시작됐다. hot table 100만 row까지 commit됐고, 125만 row 배치에서 PostgreSQL 연결이 끊겼다.

마지막 완료/실패 로그:

```text
[transaction-100m-seed] inserting hot rows 750001..1000000
INSERT 0 250000
[transaction-100m-seed] inserting hot rows 1000001..1250000
server closed the connection unexpectedly
connection to server was lost
psql: error: connection to server on socket "/var/run/postgresql/.s.PGSQL.5432" failed: FATAL: the database system is in recovery mode
```

실패 후 row count:

```text
hot|1000000
archive|0
```

## Evidence

PostgreSQL 로그:

```text
2026-04-26 00:39:37.786 KST [1] LOG: client backend (PID 202) was terminated by signal 9: Killed
2026-04-26 00:39:37.786 KST [1] DETAIL: Failed process was running:
  INSERT INTO transaction_read_model (...)
  FROM generate_series(1000001, 1250000) AS series(n);
2026-04-26 00:39:38.432 KST [217] LOG: database system was not properly shut down; automatic recovery in progress
2026-04-26 00:39:42.592 KST [217] LOG: redo done at 0/1CBFFD38
2026-04-26 00:39:42.771 KST [1] LOG: database system is ready to accept connections
```

Container state after recovery:

```text
status=running health=healthy oom=true exit=0 restartCount=0
```

cgroup snapshot after recovery:

```text
low 0
high 0
max 6243
oom 3
oom_kill 1
oom_group_kill 0
memory.current=397406208
memory.max=402653184
pids.current=12
pids.max=128
```

Index state after recovery:

```text
transaction_read_model|idx_transaction_read_model_account_cursor
transaction_read_model|pk_transaction_read_model_monthly
transaction_read_model|uq_transaction_read_model_ledger_monthly
transaction_read_model_archive|idx_transaction_read_model_archive_account_cursor
transaction_read_model_archive|pk_transaction_read_model_archive_monthly
transaction_read_model_archive|uq_transaction_read_model_archive_ledger_monthly
```

Docker stats after recovery:

```text
aquila-bank-postgres memory 166MiB / 384MiB
aquila-bank-postgres BlockIO 682MB / 1.67GB
```

## Secondary Finding

fresh DB 초기 기동 중 `postgres-exporter`가 아직 생성되지 않은 `provider_delivery_metric_summary`를 반복 조회했다.

```text
ERROR: relation "provider_delivery_metric_summary" does not exist
STATEMENT: SELECT queue_name, metric_type, metric_name, metric_count
FROM provider_delivery_metric_summary
```

주 병목은 insert backend OOM이지만, migration 완료 전 exporter query는 부하 실험 로그 노이즈와 불필요한 DB activity를 만든다.

## Bottleneck Conclusion

1. fresh volume에서도 seed가 125만 row 배치에서 OOM으로 실패했다. 이전 crash volume 영향이 아니라 현재 seed strategy와 PostgreSQL 384MiB 예산의 구조적 한계다.
2. #357의 `SEED_BATCH_SIZE=250000`은 재사용 volume 기준 475만 row까지 갔지만, fresh volume에서는 125만 row에서 명시적 OOM kill이 발생했다.
3. k6 HTTP p95/throughput은 아직 측정 대상이 아니다. 현재 우선순위는 1억 row fixture 생성과 read-only k6 실행 환경을 분리하는 것이다.
4. t3.micro 예산에서 “1억 row dataset 생성”과 “1억 row dataset 조회”를 같은 PostgreSQL 384MiB runtime에서 동시에 검증하는 방식은 측정 전 단계에서 깨진다.

## Next Actions

- fixture 생성은 seed 전용 budget으로 분리하고, 생성된 volume/snapshot을 read-only query phase에서 t3.micro budget으로 제한한다.
- t3.micro seed 계속 검증이 필요하면 `SEED_BATCH_SIZE=50000` 이하, exporter/prometheus 중지, `SEED_INDEX_STRATEGY=none|required` 분리, `SEED_CONFLICT_MODE=fail` 유지 조건을 각각 한 변수씩 측정한다.
- read 성능 목표 검증은 seed 완료 snapshot을 먼저 확보한 뒤 k6를 `--k6-only`로 반복 실행한다.
- local loadtest wrapper에 “fresh volume mode”와 “seed phase observability off” 옵션을 추가해 실험 재현성을 높인다.

## Cleanup

증거 수집 후 PostgreSQL loadtest container를 중지했다.

```bash
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml \
  --profile loadtest stop postgres
```
