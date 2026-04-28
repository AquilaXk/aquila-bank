# Transaction Read Volume Corruption Runbook

OCI A1 100m transaction read fixture data volume이 crash recovery loop에 빠졌을 때의 복구 기준입니다. 운영 RDS 절차가 아니라 OCI A1 self-managed PostgreSQL 100m performance fixture 전용입니다.

## 복구 기준

- `docker inspect aquila-bank-postgres --format '{{.State.OOMKilled}}'`가 `false`이고 container가 `running`이다.
- `tools/test/run-transaction-100m-fixture-restore.sh`의 recovery preflight가 `pg_is_in_recovery()=false`를 확인한다.
- schema/index preflight가 통과하고 read model sample row가 의도한 fixture account에서 조회된다.
- 이 조건이면 기존 volume을 유지하고 아래 순서로 검증한다.

```bash
FIXTURE_MODE=verify \
FIXTURE_VERIFY_MIN_ROWS=1000 \
tools/test/run-transaction-100m-fixture-restore.sh
```

## 폐기 기준

- `OOMKilled=true`가 남아 있다.
- container status가 `restarting`, `dead`, `exited` 중 하나이고 짧은 재시작 후에도 닫히지 않는다.
- `pg_is_in_recovery()` 확인이 connection failure 또는 startup/recovery loop 메시지로 반복 실패한다.
- 대량 cleanup 직후 PostgreSQL signal 9 또는 OCI A1 host memory pressure가 확인됐다.

이 조건에서는 mutable volume을 신뢰하지 않고 dump artifact 기준 fresh restore로 전환한다.

```bash
tools/test/run-transaction-100m-fresh-volume-restore-k6.sh --dry-run

FRESH_VOLUME_CONFIRM=erase-postgres-volume \
FIXTURE_NAME=transaction-100m-fixture \
tools/test/run-transaction-100m-fresh-volume-restore-k6.sh
```

## 재생성 기준

- `build/fixtures/<name>.dump`가 없거나 0 byte다.
- fresh restore가 `fixture dump not found`로 실패한다.
- schema migration이 바뀌어 기존 dump가 현재 Flyway schema와 맞지 않는다.

이 조건에서는 fixture 생성 phase를 다시 실행하고 dump artifact를 먼저 만든다.

```bash
SEED_TOTAL_ROWS=100000000 \
SEED_BATCH_SIZE=250000 \
SEED_TRUNCATE=true \
FIXTURE_POSTGRES_MEMORY=2g \
tools/test/prepare-transaction-read-model-100m-fixture.sh

FIXTURE_MODE=dump \
FIXTURE_NAME=transaction-100m-fixture \
tools/test/run-transaction-100m-fixture-restore.sh
```

## Cleanup 기준

100m fixture cleanup은 큰 `TRUNCATE`/`DELETE` 한 번으로 처리하지 않는다. WAL budget을 두고 account 범위 chunk delete로 정리한다.

```bash
tools/test/run-transaction-100m-fixture-cleanup-wal-budget.sh --print-plan

CLEANUP_CONFIRM=delete-100m-fixture \
CLEANUP_CHUNK_SIZE=50000 \
CLEANUP_WAL_MAX_BYTES=1073741824 \
tools/test/run-transaction-100m-fixture-cleanup-wal-budget.sh
```

cleanup 중 `WAL budget exceeded`가 나면 즉시 중단하고 volume 상태를 다시 확인한다. 이때 남은 row를 더 큰 batch로 밀어붙이지 않는다.

## 금지

- dump artifact 없이 손상 volume을 반복 재시작하며 k6를 실행하지 않는다.
- PostgreSQL recovery loop 상태에서 cleanup 또는 restore를 실행하지 않는다.
- 운영 URL, token, RDS 접속 정보는 성능 결과 문서나 runbook 명령에 남기지 않는다.
