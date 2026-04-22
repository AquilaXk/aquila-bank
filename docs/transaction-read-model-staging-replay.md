# Transaction Read Model Staging Replay

## Purpose

`Transaction Read Model Staging Replay` workflow는 로컬 fixture가 아니라 staging/RDS의 1억 건 분포에서 hot/cold 거래 조회 p95를 검증합니다.

- hot: `GET /api/v1/transactions`
- cold: `GET /api/v1/transactions/archive`
- DB 분포 확인: `transaction_read_model` + `transaction_read_model_archive`의 `pg_class.reltuples` estimate
- p95 산출: hot first, hot cursor, cold first, cold cursor

## Prerequisites

- staging GitHub Environment secrets:
  - `STAGING_BASE_URL`
  - `STAGING_REPLAY_TOKEN`
  - `STAGING_RDS_DATABASE_URL`
- staging RDS 통계가 최신이어야 합니다.
  - 1억 건 분포 적재 또는 replay 전후 `ANALYZE` 수행
  - full count 대신 `pg_class.reltuples` estimate를 쓰므로 오래된 통계는 검증 실패나 과소/과대 평가를 만들 수 있습니다.
- cold path는 `GET /api/v1/transactions/archive` 배포 이후 실행합니다.

## Workflow Inputs

- `hot_account_id`: hot table에 cursor page가 나올 만큼 row가 있는 account id
- `hot_from`, `hot_to`: hot 조회 기간, 최대 31일
- `cold_account_id`: archive table에 cursor page가 나올 만큼 row가 있는 account id
- `cold_from`, `cold_to`: cold 조회 기간, 최대 31일
- `iterations`: shape별 반복 횟수, 기본 `40`
- `page_limit`: API page size, 기본 `50`, 최대 `100`
- `expected_total_rows`: hot + archive RDS estimate 최소값, 기본 `100000000`
- `hot_p95_threshold_ms`: hot first/cursor p95 기준, 기본 `350`
- `cold_p95_threshold_ms`: cold first/cursor p95 기준, 기본 `750`

## Gate Behavior

- RDS estimate가 `expected_total_rows`보다 작으면 실패합니다.
- hot/cold account에 row가 없으면 실패합니다.
- 각 first page가 `nextCursor`를 반환하지 않으면 cursor replay가 불가능하므로 실패합니다.
- HTTP non-2xx, timeout, empty `items` 응답은 실패합니다.
- hot first/cursor p95 중 하나라도 hot threshold를 넘으면 실패합니다.
- cold first/cursor p95 중 하나라도 cold threshold를 넘으면 실패합니다.

## Output

workflow는 `transaction-read-model-staging-replay` artifact를 남깁니다.

- `summary.json`: threshold, p95, RDS estimate, 실패 여부
- `summary.md`: GitHub step summary용 요약
- `*.ms`: shape별 latency sample
- `*.json`: 각 API 응답 body sample

## Local Syntax Check

```bash
bash -n tools/ops/transaction-read-model-staging-replay.sh
ruby -e "require 'yaml'; YAML.load_file('.github/workflows/transaction-read-model-staging-replay.yml'); puts 'ok'"
```

## Rollback

workflow/script/doc만 추가하므로 rollback은 PR revert로 수행합니다. staging/RDS 데이터나 production promotion 상태는 변경하지 않습니다.
