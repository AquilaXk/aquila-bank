# transaction-100m-local-t3micro-20260425-seed-stability-rerun-bottleneck

## Summary

- 실행일: 2026-04-25 KST
- Git 기준: `0fd4905` (`main`, #357 seed stability 포함)
- 환경: local Docker compose `compose.yml` + `compose.t3micro.yml` + `compose.loadtest.yml`
- 목표: 1억 row 거래 조회 seed 후 k6 HTTP 부하 테스트로 병목 확인
- 결과: k6 HTTP 부하 단계까지 도달하지 못함
- 주 병목: PostgreSQL 384MiB 제한에서 required account cursor index를 유지한 hot table insert가 475만 row 배치 중 PostgreSQL background writer kill/recovery를 유발

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
- 실행 전 disk: `156GiB` available
- 실패 후 disk: `163GiB` available
- Flyway preflight: `applied=52 required=52`

## Failure Point

최신 runner는 stale backend 방지를 위해 backend를 force-recreate했고 Flyway 최신 버전을 확인했다. seed는 정상 시작했지만 hot table 450만 row까지만 commit됐다.

마지막 완료/실패 로그:

```text
[transaction-100m-seed] inserting hot rows 4250001..4500000
INSERT 0 250000
[transaction-100m-seed] inserting hot rows 4500001..4750000
WARNING: terminating connection because of crash of another server process
DETAIL: The postmaster has commanded this server process to roll back the current transaction and exit, because another server process exited abnormally and possibly corrupted shared memory.
server closed the connection unexpectedly
connection to server was lost
psql: error: connection to server on socket "/var/run/postgresql/.s.PGSQL.5432" failed: FATAL: the database system is in recovery mode
```

실패 후 row count:

```text
hot|4500000
archive|0
```

## Evidence

PostgreSQL 로그:

```text
2026-04-25 23:56:39.612 KST [1] LOG: background writer process (PID 31) was terminated by signal 9: Killed
2026-04-25 23:56:39.612 KST [1] LOG: terminating any other active server processes
2026-04-25 23:56:40.112 KST [282] LOG: database system was not properly shut down; automatic recovery in progress
2026-04-25 23:56:50.599 KST [1] LOG: startup process (PID 282) was terminated by signal 9: Killed
2026-04-25 23:57:06.893 KST [32] LOG: redo done at C/F3FFFF38
2026-04-25 23:57:07.227 KST [1] LOG: database system is ready to accept connections
```

Container state after recovery:

```text
status=running health=healthy oom=false restartCount=1
```

cgroup snapshot after recovery:

```text
low 0
high 0
max 13693
oom 0
oom_kill 0
oom_group_kill 0
memory.current=394907648
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
aquila-bank-postgres memory 161.5MiB / 384MiB
aquila-bank-postgres BlockIO 2.82GB / 1.29GB
```

## Bottleneck Conclusion

1. #357의 `250,000` batch 축소와 Flyway preflight는 stale schema 문제를 해결했지만, 384MiB PostgreSQL seed write path 자체는 아직 1억 row까지 도달하지 못한다.
2. 실패 지점은 이전 1,800만 row에서 475만 row 배치로 앞당겨졌다. 이전 crash 이후 재사용 volume의 WAL/recovery 이력과 누적 파일 상태가 영향을 줬을 가능성이 있다.
3. container-level `OOMKilled=false`여도 PostgreSQL 하위 process가 `signal 9`로 종료될 수 있다. cgroup `memory.current`가 `memory.max`에 근접했고 `memory.events max`가 증가한 상태라 memory pressure를 우선 병목으로 본다.
4. k6 HTTP p95/throughput 병목은 아직 측정하지 못했다. 현재 최우선 병목은 seed phase 안정성이다.

## Next Actions

- fresh PostgreSQL volume에서 동일 runner를 재실행해 “기존 crash volume 영향”과 “순수 seed strategy 영향”을 분리한다.
- seed phase와 query phase의 resource budget을 분리한다. 1억 row fixture 생성은 운영 t3.micro 예산이 아니라 fixture 생성 예산에서 수행하고, query phase만 t3.micro budget으로 제한하는 방식이 더 현실적이다.
- truncate 기반 신규 seed에서는 required index 유지와 `ON CONFLICT` 비용을 분리 측정한다.
- `SEED_BATCH_SIZE=50000~100000` 범위에서 memory headroom과 WAL recovery 안정성을 추가 측정한다.
- seed가 완료된 snapshot volume을 만든 뒤 k6 read-only 부하를 반복 실행한다.

## Cleanup

증거 수집 후 PostgreSQL loadtest container를 중지했다.

```bash
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml \
  --profile loadtest stop postgres
```
