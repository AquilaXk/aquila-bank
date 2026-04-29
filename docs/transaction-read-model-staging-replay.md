# Transaction Read Model Staging Replay

## Purpose

`Transaction Read Model Staging Replay`는 수동 workflow와 staging deploy release gate가 같은 script를 공유하며, OCI A1/staging PostgreSQL의 1억 건 분포에서 hot/cold 거래 조회 p95를 검증합니다. 현재 1억 건 primary evidence는 OCI A1 Flex 4 OCPU / 24GB + data 300GB self-managed PostgreSQL fixture입니다. 기존 workflow secret 이름의 `RDS`는 호환성 때문에 유지하지만 값은 OCI A1 PostgreSQL 접속 URL을 넣습니다.

- hot: `GET /api/v1/transactions`
- cold: `GET /api/v1/transactions/archive`
- DB 분포 확인: `transaction_read_model` + `transaction_read_model_archive`의 leaf partition `pg_class.reltuples` estimate 합계
- p95 산출: hot first, hot cursor, cold first, cold cursor

## Prerequisites

- staging GitHub Environment secrets:
  - `STAGING_BASE_URL`
  - `STAGING_REPLAY_TOKEN`
  - `STAGING_OCI_A1_DATABASE_URL`
  - `STAGING_RDS_DATABASE_URL`는 legacy fallback으로만 사용
- OCI A1 100m primary evidence:
  - `tools/test/prepare-transaction-read-model-100m-fixture.sh`
  - `tools/test/run-transaction-read-model-100m-k6-local.sh --k6-only`
- staging deploy release gate용 추가 secrets:
  - `STAGING_REPLAY_HOT_ACCOUNT_ID`
  - `STAGING_REPLAY_HOT_FROM`
  - `STAGING_REPLAY_HOT_TO`
  - `STAGING_REPLAY_COLD_ACCOUNT_ID`
  - `STAGING_REPLAY_COLD_FROM`
  - `STAGING_REPLAY_COLD_TO`
- staging/OCI A1 PostgreSQL 통계가 최신이어야 합니다.
  - 1억 건 분포 적재 또는 replay 전후 `ANALYZE` 수행
  - full count 대신 `pg_class.reltuples` estimate를 쓰므로 오래된 통계는 검증 실패나 과소/과대 평가를 만들 수 있습니다.
- 월별 partition lifecycle은 replay 전에 확인합니다.
  - 다음 기간 partition 선생성: `tools/ops/transaction-read-model-chunk-lifecycle.sh --action precreate --target both`
  - partition별 stats 갱신: `tools/ops/transaction-read-model-chunk-lifecycle.sh --action analyze --target both`
- cold path는 `GET /api/v1/transactions/archive` 배포 이후 실행합니다.

## Staging Deploy Release Gate

- `Staging Deploy` workflow는 OCI A1 SSH deploy와 OCI A1 PostgreSQL replay secret이 준비된 환경에서 post-deploy smoke 뒤에 같은 replay script를 실행합니다.
- release gate는 아래 `staging` Environment secret을 읽어 수동 입력 없이 same SHA를 검증합니다.
  - required:
    - `STAGING_REPLAY_HOT_ACCOUNT_ID`
    - `STAGING_REPLAY_HOT_FROM`
    - `STAGING_REPLAY_HOT_TO`
    - `STAGING_REPLAY_COLD_ACCOUNT_ID`
    - `STAGING_REPLAY_COLD_FROM`
    - `STAGING_REPLAY_COLD_TO`
  - optional:
    - `STAGING_REPLAY_REQUIRED`
    - `STAGING_REPLAY_ITERATIONS`
    - `STAGING_REPLAY_PAGE_LIMIT`
    - `STAGING_REPLAY_REQUEST_TIMEOUT_SECONDS`
    - `STAGING_REPLAY_EXPECTED_TOTAL_ROWS`
    - `STAGING_REPLAY_HOT_P95_THRESHOLD_MS`
    - `STAGING_REPLAY_COLD_P95_THRESHOLD_MS`
    - `STAGING_REPLAY_STATS_MAX_AGE_HOURS`
    - `STAGING_REPLAY_STATS_MAX_MODIFIED_RATIO`
- replay gate가 실행되고 실패하면 `staging-100m-replay` evidence status가 `failure`로 기록되어 production promotion이 같은 SHA를 통과시키지 않습니다.
- 앱 staging CD는 OCI A1 blue/green deploy와 post-deploy smoke가 성공하면 성공으로 기록합니다. 100m fixture가 비어 있는 경우 replay report/artifact를 남기고 production promotion만 차단합니다.
- OCI A1 secret 조건이 맞지 않는 환경에서는 이 gate를 성공으로 간주하지 않고, 1억 건 primary evidence가 blocked 상태로 남습니다.

## Workflow Inputs

- `hot_account_id`: hot table에 cursor page가 나올 만큼 row가 있는 account id
- `hot_from`, `hot_to`: hot 조회 기간, 최대 31일
- `cold_account_id`: archive table에 cursor page가 나올 만큼 row가 있는 account id
- `cold_from`, `cold_to`: cold 조회 기간, 최대 31일
- `iterations`: shape별 반복 횟수, 기본 `40`
- `page_limit`: API page size, 기본 `50`, 최대 `100`
- `expected_total_rows`: hot + archive PostgreSQL estimate 최소값, 기본 `100000000`
- `hot_p95_threshold_ms`: hot first/cursor p95 기준, 기본 `350`
- `cold_p95_threshold_ms`: cold first/cursor p95 기준, 기본 `750`

## Gate Behavior

- planner stats freshness guard가 `transaction_read_model`, `transaction_read_model_archive`의 leaf partition analyze 시각과 `n_mod_since_analyze / reltuples` 비율을 먼저 확인합니다.
- freshness guard가 stale stats를 감지하면 table별 `tools/ops/transaction-read-model-chunk-lifecycle.sh --action analyze --target <hot|archive>` guidance와 함께 즉시 실패합니다.
- PostgreSQL estimate가 `expected_total_rows`보다 작으면 실패합니다.
- estimate가 0이면 `fixture_missing`으로 분류하고 fixture restore/analyze guidance와 함께 report를 남깁니다.
- hot/cold account에 row가 없으면 실패합니다.
- 각 first page가 `nextCursor`를 반환하지 않으면 cursor replay가 불가능하므로 실패합니다.
- HTTP non-2xx, timeout, empty `items` 응답은 실패합니다.
- hot first/cursor p95 중 하나라도 hot threshold를 넘으면 실패합니다.
- cold first/cursor p95 중 하나라도 cold threshold를 넘으면 실패합니다.

## Output

workflow는 `transaction-read-model-staging-replay` artifact를 남깁니다.

- `summary.json`: threshold, p95, PostgreSQL estimate, 실패 여부
- `summary.md`: GitHub step summary용 요약
- `*.ms`: shape별 latency sample
- `*.json`: 각 API 응답 body sample

## Local Syntax Check

```bash
bash -n tools/ops/transaction-read-model-staging-replay.sh
ruby -e "require 'yaml'; YAML.load_file('.github/workflows/transaction-read-model-staging-replay.yml'); puts 'ok'"
```

## Rollback

workflow/script/doc만 추가하므로 rollback은 PR revert로 수행합니다. staging/OCI A1 데이터나 production promotion 상태는 변경하지 않습니다. OCI A1 replay를 운영하지 않는 기간에는 1억 건 primary evidence를 통과로 판정하지 않습니다.
