# Performance Result Archive

성능 테스트 결과는 실행 단위마다 Markdown으로 남깁니다.

## 원칙

- k6, Docker t3.micro smoke, staging replay, transaction regression gate 결과는 사람이 다시 읽을 수 있는 Markdown 요약을 남깁니다.
- 원본 JSON/latency sample은 `build/reports/**`에 두고, 리뷰/공유용 요약은 이 디렉터리에 둡니다.
- token, JWT, 운영 URL, 개인 식별자는 결과 문서에 기록하지 않습니다.
- 실패한 실행도 원인과 마지막 확인 지점을 문서화합니다.

## 파일명

```text
YYYY-MM-DD-<environment>-<workload>.md
```

예시:

```text
2026-04-25-docker-t3micro-local-smoke.md
2026-04-25-local-loadtest-transaction-100m.md
```

## k6 1억 건 거래 조회

`tools/test/run-k6-transaction-100m-loadtest.sh`는 k6 summary Markdown을 생성한 뒤 기본적으로 이 디렉터리에 복사합니다.

실행 결과:

- [transaction-100m-small-smoke-summary.md](transaction-100m-small-smoke-summary.md)
- [transaction-100m-local-t3micro-20260425-bottleneck.md](transaction-100m-local-t3micro-20260425-bottleneck.md)
- [transaction-100m-required-index-smoke-summary.md](transaction-100m-required-index-smoke-summary.md)

로컬 1억 건 synthetic read model 검증은 생성 phase와 조회 phase를 분리합니다. t3.micro PostgreSQL 384MiB에서 dataset 생성까지 함께 제한하면 seed OOM이 먼저 발생해 read bottleneck을 측정하지 못합니다.

### Phase 1. fixture 생성

1억 row dataset 생성은 fixture 전용 PostgreSQL budget에서 실행합니다. 이 phase는 기본적으로 Prometheus/Grafana/Postgres exporter/k6를 띄우지 않고 `postgres`와 `aquila-bank-backend`만 사용합니다.

```bash
SEED_TOTAL_ROWS=100000000 \
SEED_BATCH_SIZE=250000 \
SEED_TRUNCATE=true \
FIXTURE_POSTGRES_MEMORY=2g \
tools/test/prepare-transaction-read-model-100m-fixture.sh
```

실행 전 plan을 먼저 확인합니다.

```bash
tools/test/prepare-transaction-read-model-100m-fixture.sh --print-plan
```

기본값은 `SEED_INDEX_STRATEGY=required`입니다. 이 전략은 hot/archive account cursor index를 빈 table 상태에서 먼저 유지해, t3.micro PostgreSQL에서 5천만 row btree를 사후 build하다 OOM 나는 경로를 피합니다.

기본 batch는 `SEED_BATCH_SIZE=250000`입니다. PostgreSQL 384MiB cgroup에서 100만 row 단위 insert가 recovery loop를 만든 전례가 있어 local t3.micro 경로는 더 작은 batch를 기본으로 둡니다.

기본 conflict mode는 `SEED_CONFLICT_MODE=fail`입니다. `SEED_TRUNCATE=true` 신규 seed는 중복 가능성이 없어 `ON CONFLICT` 비용을 제거합니다. 기존 dataset 위에 idempotent append를 의도할 때만 `SEED_TRUNCATE=false SEED_CONFLICT_MODE=ignore`를 명시합니다.

fixture prepare runner는 backend bootJar를 만든 뒤 `aquila-bank-backend`를 force-recreate하고, DB의 `flyway_schema_history` 최신 version이 로컬 migration 파일 최신 version 이상인지 확인한 뒤 seed를 실행합니다.

`SEED_INDEX_STRATEGY=rebuild-all`은 전체 secondary filter index를 drop 후 재생성합니다. 이 모드는 partition/chunk 구조 검증 또는 더 큰 memory budget에서만 사용하고, t3.micro 측정 경로에서는 기본값으로 사용하지 않습니다.

### Phase 2. t3.micro k6 조회

fixture 생성이 끝난 volume을 유지한 상태에서 조회 phase만 t3.micro budget으로 실행합니다.

```bash
K6_HOT_ACCOUNT_ID=910000001 \
K6_COLD_ACCOUNT_ID=910000002 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
tools/test/run-transaction-read-model-100m-k6-local.sh --k6-only
```

기존 `tools/test/run-transaction-read-model-100m-k6-local.sh` 단일 실행은 seed와 k6를 이어서 수행하는 호환 경로입니다. 1억 row read bottleneck 검증에서는 생성과 조회를 분리한 두 phase 절차를 우선 사용합니다.

실행 전 확인값:

- local disk 여유 공간
- Docker Desktop memory/disk limit
- fixture phase의 `FIXTURE_POSTGRES_MEMORY`, `FIXTURE_POSTGRES_CPUS`, `FIXTURE_POSTGRES_PIDS_LIMIT`
- query phase의 `compose.loadtest.yml` backend `2 vCPU / 1GiB` budget과 PostgreSQL t3.micro budget
- PostgreSQL container가 이전 실행에서 `OOMKilled=true`로 남아 있지 않은지 여부
- `tools/test/prepare-transaction-read-model-100m-fixture.sh --print-plan`의 fixture budget/batch/conflict/Flyway preflight 값
- `tools/test/run-transaction-read-model-100m-k6-local.sh --k6-only` 실행 전 hot/cold account와 기간 값
- hot/cold account와 기간 기본값이 테스트 의도와 맞는지 여부

수동 보관이 필요하면 아래 명령을 사용합니다.

```bash
tools/test/archive-k6-transaction-100m-result.sh \
  build/reports/k6/<name>-summary.md \
  build/reports/k6/<name>-summary.json
```

## Admission guard telemetry

방어형 HTTP admission smoke 결과는 summary TSV와 raw TSV를 Markdown으로 보관합니다. token, Authorization header, 운영 URL은 결과 문서에 남기지 않습니다.

```bash
tools/test/archive-admission-guard-telemetry-snapshot.sh \
  build/reports/admission/<name>/http-admission-summary.tsv \
  build/reports/admission/<name>/http-admission-raw.tsv
```

## 월별 chunk lifecycle

월별 partition 선생성, archive detach/drop guard, partition별 `ANALYZE`/`VACUUM` 절차는 [Transaction Read Model Chunk Lifecycle Runbook](../transaction-read-model-chunk-lifecycle.md)을 기준으로 실행합니다.

1억 row SLO 검증 전에는 다음 순서를 지킵니다.

```bash
tools/ops/transaction-read-model-chunk-lifecycle.sh --action precreate --target both --print-sql
tools/ops/transaction-read-model-chunk-lifecycle.sh --action analyze --target both --print-sql
tools/test/run-transaction-read-model-100m-k6-local.sh --print-plan
```
