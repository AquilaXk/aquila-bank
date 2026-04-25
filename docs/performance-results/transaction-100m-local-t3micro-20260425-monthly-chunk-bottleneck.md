# transaction-100m-local-t3micro-20260425-monthly-chunk-bottleneck

## Summary

- 실행일: 2026-04-25 KST
- Git 기준: `904feb1` (`main`, #350 monthly chunk + #351 lifecycle 포함)
- 환경: local Docker compose `compose.yml` + `compose.t3micro.yml` + `compose.loadtest.yml`
- 목표: 1억 row 거래 조회 seed 후 k6 HTTP 부하 테스트로 병목 확인
- 결과: k6 HTTP 부하 단계까지 도달하지 못함
- 주 병목: PostgreSQL 384MiB 제한에서 required account cursor index를 유지한 hot table 대량 insert가 1,800만 row 배치 중 PostgreSQL process kill/recovery loop를 유발

## Command

```bash
tools/test/run-transaction-read-model-100m-k6-local.sh
```

기본 실행값:

```text
SEED_TOTAL_ROWS=100000000
SEED_TRUNCATE=true
SEED_INDEX_STRATEGY=required
SEED_HOT_ROWS=50000000
SEED_BATCH_SIZE=1000000
K6_VUS=8
K6_DURATION=1m
K6_LIMIT=50
```

## Environment

- PostgreSQL: `postgres:18`, cgroup memory `384MiB`, pids `128`
- Backend: `eclipse-temurin:21-jre`, cgroup memory `1GiB`
- Observability: Prometheus `9090`, Grafana `3001`, Alertmanager `9093`, postgres-exporter `9187`
- 실행 전 disk: `169GiB` available
- 실패 후 disk: `162GiB` available

## Preflight Finding

첫 실행은 최신 `main` 스키마가 아닌 stale runtime 때문에 중단됐다.

```text
flyway latest before backend recreate: 51
transaction_read_model.id: GENERATED ALWAYS identity
seed error: cannot insert a non-DEFAULT value into column "id"
```

조치:

```bash
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml \
  --profile loadtest up -d --force-recreate aquila-bank-backend
```

재생성 후 Flyway V52 적용 확인:

```text
52|partition transaction read model monthly|t
```

## Failure Point

최신 스키마에서 seed는 정상 시작했고 hot table 1,700만 row까지 commit됐다.

마지막 완료/실패 로그:

```text
[transaction-100m-seed] inserting hot rows 16000001..17000000
INSERT 0 1000000
[transaction-100m-seed] inserting hot rows 17000001..18000000
server closed the connection unexpectedly
connection to server was lost
psql: error: connection to server on socket "/var/run/postgresql/.s.PGSQL.5432" failed: FATAL: the database system is in recovery mode
```

DB가 recovery loop에 들어가 row count 확인 쿼리는 실행하지 못했다. 배치 로그 기준 durable row는 hot table 17,000,000 row까지로 판단한다.

## Evidence

PostgreSQL 로그:

```text
2026-04-25 09:13:28.358 KST [1] LOG: client backend (PID 24427) was terminated by signal 9: Killed
2026-04-25 09:13:28.358 KST [1] DETAIL: Failed process was running:
  INSERT INTO transaction_read_model (...)
  FROM generate_series(17000001, 18000000) AS series(n)
  ON CONFLICT (id, booked_at) DO NOTHING;
2026-04-25 09:13:29.494 KST [24443] LOG: database system was not properly shut down; automatic recovery in progress
2026-04-25 09:13:33.645 KST [1] LOG: startup process (PID 24443) was terminated by signal 9: Killed
2026-04-25 09:13:48.532 KST [1] LOG: startup process (PID 32) was terminated by signal 9: Killed
2026-04-25 09:13:58.785 KST [1] LOG: startup process (PID 32) was terminated by signal 9: Killed
```

Container state:

```text
status=running health=starting oom=false restartCount=3
```

cgroup snapshot:

```text
memory.current=402259968
memory.max=402653184
pids.current=9
pids.max=128
cpu nr_throttled=79
```

Docker stats near failure:

```text
aquila-bank-postgres CPU      63.31%
aquila-bank-postgres memory   161.3MiB / 384MiB
aquila-bank-postgres BlockIO  3.38GB / 22.7GB
```

Recovery 중 cgroup memory는 `memory.max`에 거의 붙어 있었고 startup process가 반복적으로 signal 9로 종료됐다.

## Bottleneck Conclusion

1. monthly chunk 전환 후 이전의 “5천만 row 단일 btree index build OOM” 병목은 제거됐지만, 384MiB PostgreSQL에서 100만 row 단위 `INSERT ... generate_series ... ON CONFLICT`와 required index 유지 비용이 여전히 너무 크다.
2. k6 HTTP 부하 테스트보다 seed write path가 먼저 실패한다. 현재 로컬 t3.micro 조건에서 1억 row HTTP p95 검증은 seed batch 크기/메모리/WAL 복구 안정성을 먼저 낮춰야 가능하다.
3. 실패는 디스크 부족이 아니다. 여유 공간은 162GiB였고, PostgreSQL process/recovery startup이 signal 9로 종료됐다.
4. `docker inspect`의 container-level `OOMKilled=false`만으로 안전하다고 판단하면 안 된다. PostgreSQL 하위 process kill과 cgroup `memory.current ~= memory.max`를 함께 봐야 한다.
5. loadtest wrapper는 running container가 오래 떠 있으면 최신 Flyway를 적용하지 못한다. 실제 최신 main 검증 전 backend force-recreate가 필요하다.

## Next Actions

- seed batch를 100만 row에서 10만~25만 row 범위로 낮추고, t3.micro cgroup에서 recovery loop 없이 1억 row까지 도달하는지 재검증한다.
- seed 전 backend force-recreate 또는 Flyway latest version preflight를 loadtest wrapper에 추가한다.
- `INSERT ... ON CONFLICT`가 1억 seed에 필요한지 분리한다. truncate 기반 신규 seed에서는 conflict check를 제거하거나 별도 idempotent mode로 제한한다.
- PostgreSQL cgroup memory headroom을 보장하도록 loadtest profile에서 seed phase와 query phase의 memory/WAL 설정을 분리한다.
- seed 성공 후에만 k6를 실행하고, 실패 시 k6 미실행을 정상적인 fail-fast 결과로 문서화한다.

## Cleanup

반복 recovery loop로 로컬 자원을 계속 쓰지 않도록 증거 수집 후 PostgreSQL loadtest container를 중지했다.

```bash
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml \
  --profile loadtest stop postgres
```
