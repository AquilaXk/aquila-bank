# Performance Result Archive

성능 테스트 결과는 실행 단위마다 Markdown으로 남깁니다.

## 원칙

- k6, local Docker 100m, Docker t3.micro smoke, staging replay, transaction regression gate 결과는 사람이 다시 읽을 수 있는 Markdown 요약을 남깁니다.
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

로컬 1억 건 synthetic read model 검증은 생성 phase와 조회 phase를 분리합니다. primary evidence는 로컬 Docker PostgreSQL 18 + 로컬 디스크/volume에 1억 row fixture를 적재한 뒤 로컬 k6로 bounded query를 측정하는 결과입니다. t3.micro PostgreSQL 384MiB에서 dataset 생성까지 함께 제한하면 seed OOM이 먼저 발생해 read bottleneck을 측정하지 못합니다.

### Phase 1. fixture 생성

1억 row dataset 생성은 fixture 전용 PostgreSQL budget과 로컬 Docker volume에서 실행합니다. 이 phase는 기본적으로 Prometheus/Grafana/Postgres exporter/k6를 띄우지 않고 `postgres`와 `aquila-bank-backend`만 사용합니다.

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

### Phase 2. local k6 조회

fixture 생성이 끝난 volume을 유지한 상태에서 조회 phase를 로컬 k6로 실행합니다. 작은 운영 budget 검증이 필요하면 backend/PostgreSQL만 Docker cgroup 제한으로 낮추고, 1억 건 적재 자체는 기존 local disk/volume을 재사용합니다.

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
- query phase의 `compose.loadtest.yml` backend `2 vCPU / 1GiB` budget과 PostgreSQL Docker budget
- PostgreSQL container가 이전 실행에서 `OOMKilled=true`로 남아 있지 않은지 여부
- `tools/test/prepare-transaction-read-model-100m-fixture.sh --print-plan`의 fixture budget/batch/conflict/Flyway preflight 값
- `tools/test/run-transaction-read-model-100m-k6-local.sh --k6-only` 실행 전 hot/cold account와 기간 값
- hot/cold account와 기간 기본값이 테스트 의도와 맞는지 여부

volume이 `OOMKilled=true` 또는 recovery loop 상태로 남으면 [Transaction Read Volume Corruption Runbook](../transaction-read-volume-corruption-runbook.md)을 기준으로 복구/폐기/재생성을 판단합니다.

수동 보관이 필요하면 아래 명령을 사용합니다.

```bash
tools/test/archive-k6-transaction-100m-result.sh \
  build/reports/k6/<name>-summary.md \
  build/reports/k6/<name>-summary.json
```

### Fixture artifact ready gate

fresh-volume restore/k6는 local PostgreSQL volume 삭제 전에 dump artifact를 먼저 검증합니다. dump, manifest, checksum, Flyway version, row distribution 기준을 모두 통과해야 restore 경로로 들어갑니다.

```bash
tools/test/validate-transaction-100m-fixture-artifact.sh --verify
tools/test/run-transaction-100m-fresh-volume-restore-k6.sh --dry-run
```

dump가 없는 환경은 기본값 `FRESH_VOLUME_DUMP_MISSING_MODE=fail-only`로 즉시 실패합니다. dump 없이 새로 seed만 수행할 때만 `FRESH_VOLUME_DUMP_MISSING_MODE=seed-only`를 명시합니다. 이 fallback은 이름 그대로 seed-only 기준이며, k6까지 이어서 실행하려면 `FRESH_VOLUME_K6_AFTER_SEED=true`를 별도로 켭니다.

이미 restore된 local DB에서 actual k6만 실행할 때는 artifact-ready gate를 통과한 뒤 `--no-up --no-deps` k6 runner로 연결합니다.

```bash
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
tools/test/run-transaction-100m-artifact-ready-k6.sh
```

### Fixture dump publish workflow

100m dump는 Git에 넣지 않습니다. `.github/workflows/transaction-100m-fixture-dump-publish.yml`를 수동 실행해 `build/fixtures/<name>.dump`, `<name>.dump.manifest`, `<name>.dump.sha256`를 GitHub artifact로 게시합니다.

workflow는 `prepare-transaction-read-model-100m-fixture.sh`로 seed를 만든 뒤 `run-transaction-100m-fixture-restore.sh`의 dump mode에서 manifest/checksum을 기록하고, 업로드 직전 `validate-transaction-100m-fixture-artifact.sh --verify`로 다시 검증합니다.

### Defensive local launchers

HTTP admission smoke는 live backend와 test data가 준비돼 있어야 의미가 있습니다. local compose에서 backend와 PostgreSQL을 띄우고 작은 seed를 넣은 뒤 admission smoke를 실행합니다.

```bash
ADMISSION_NAME=local-admission-smoke \
tools/test/run-defensive-runtime-http-admission-compose.sh
```

outbox backlog gate는 internal service JWT와 ops endpoint enable이 필요합니다. local runner는 compose backend에 ops endpoint를 켜고, `issue-internal-service-token.sh`로 `internal:outbox-ops` service JWT를 발급한 뒤 backlog gate를 실행합니다. token 값은 report에 기록하지 않습니다.

```bash
OUTBOX_BACKLOG_NAME=local-outbox-backlog \
tools/test/run-outbox-provider-backlog-local-gate.sh
```

### t3.micro defensive matrix and aggregate

Docker cgroup smoke archive에는 status/budget뿐 아니라 Docker stats sample과 GC log 경로도 남깁니다. peak CPU, peak memory, peak pid는 aggregate runner가 같은 표로 묶습니다.

```bash
tools/test/run-docker-t3micro-capacity-smoke.sh --dry-run
tools/test/run-sse-reconnect-storm-t3micro-gate.sh --dry-run
```

반복 안정성은 capacity repeat matrix와 SSE reconnect concurrent-user matrix로 분리합니다.

```bash
T3MICRO_CAPACITY_SOAK_REPEATS=1,3 \
tools/test/run-t3micro-capacity-repeat-soak-matrix.sh --dry-run

SSE_RECONNECT_CLIENT_MATRIX=1,3,8 \
SSE_RECONNECT_ROUNDS=3 \
tools/test/run-sse-reconnect-concurrent-user-matrix.sh --dry-run
```

개별 gate 결과가 준비되면 한 Markdown report로 묶습니다.

```bash
T3MICRO_CAPACITY_RESULT_MD=docs/performance-results/<capacity>.md \
T3MICRO_SSE_RESULT_MD=docs/performance-results/<sse>.md \
T3MICRO_ADMISSION_SUMMARY_TSV=build/reports/admission/<name>/http-admission-summary.tsv \
T3MICRO_OUTBOX_SUMMARY_TSV=build/reports/outbox/<name>/outbox-provider-backlog-summary.tsv \
tools/test/run-t3micro-defensive-gates-aggregate-report.sh
```

local Docker 100m 결과와 staging RDS gp3 결과는 같은 k6 archive Markdown 형식끼리 비교합니다. staging RDS 결과는 선택 비교값이며, local Docker 100m primary evidence를 대체하지 않습니다.

```bash
LOCAL_K6_SUMMARY_MD=docs/performance-results/<local-100m>.md \
STAGING_RDS_K6_SUMMARY_MD=docs/performance-results/<staging-rds-gp3>.md \
tools/test/compare-transaction-100m-local-vs-staging-rds.sh
```

## Admission guard telemetry

방어형 HTTP admission smoke 결과는 summary TSV와 raw TSV를 Markdown으로 보관합니다. token, Authorization header, 운영 URL은 결과 문서에 남기지 않습니다.

```bash
tools/test/archive-admission-guard-telemetry-snapshot.sh \
  build/reports/admission/<name>/http-admission-summary.tsv \
  build/reports/admission/<name>/http-admission-raw.tsv
```

## Optional Staging RDS gp3 smoke

RDS gp3 staging read-only smoke는 [Transaction 100m Staging RDS gp3 Smoke](../transaction-100m-staging-rds-gp3-smoke.md)를 기준으로 실행합니다. 이 경로는 remote 배포/비교 smoke이며, free-tier/cost 조건이 맞지 않으면 실행하지 않습니다.

## EC2 App + local DB smoke

AWS에는 App EC2만 두고 1억 건 PostgreSQL fixture는 OCI A1 PostgreSQL data volume에 둡니다. EC2 backend가 SSH tunnel 또는 VPN으로 OCI A1 DB에 붙는 legacy 진단 경로는 [Legacy Manual Blue/Green Deploy](../../ops/deploy/ec2/README.md)의 capacity smoke 절차를 따릅니다.

권장 실행 순서:

```bash
EC2_LOCAL_DB_CAPACITY_ENV_FILE=.env/ec2-local-db-capacity.env \
  tools/test/run-ec2-local-db-capacity-env-doctor.sh

EC2_LOCAL_DB_CAPACITY_ENV_FILE=.env/ec2-local-db-capacity.env \
  tools/test/run-ec2-direct-backend-100m-k6-smoke.sh

EC2_LOCAL_DB_CAPACITY_ENV_FILE=.env/ec2-local-db-capacity.env \
  tools/test/run-ec2-nginx-100m-k6-smoke.sh

EC2_LOCAL_DB_CAPACITY_ENV_FILE=.env/ec2-local-db-capacity.env \
  tools/test/run-ec2-local-db-307-burst-gate.sh
```

direct backend와 nginx 결과는 같은 run id의 k6 Markdown summary끼리 비교합니다.

```bash
EC2_DIRECT_K6_SUMMARY_MD=build/reports/k6/<run>-direct-backend-summary.md \
EC2_NGINX_K6_SUMMARY_MD=build/reports/k6/<run>-nginx-summary.md \
  tools/test/compare-ec2-direct-vs-nginx-latency.sh
```

## 월별 chunk lifecycle

월별 partition 선생성, archive detach/drop guard, partition별 `ANALYZE`/`VACUUM` 절차는 [Transaction Read Model Chunk Lifecycle Runbook](../transaction-read-model-chunk-lifecycle.md)을 기준으로 실행합니다.

1억 row SLO 검증 전에는 다음 순서를 지킵니다.

```bash
tools/ops/transaction-read-model-chunk-lifecycle.sh --action precreate --target both --print-sql
tools/ops/transaction-read-model-chunk-lifecycle.sh --action analyze --target both --print-sql
tools/test/run-transaction-read-model-100m-k6-local.sh --print-plan
```
