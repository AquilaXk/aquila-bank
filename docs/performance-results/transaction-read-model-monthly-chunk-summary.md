# transaction-read-model-monthly-chunk-summary

## Scope

- issue: `#350`
- 목적: `transaction_read_model`과 `transaction_read_model_archive`를 `booked_at` 월별 range partition parent로 전환
- 실제 1억 row 부하 테스트 결과가 아니라, 1억 row 적재 전에 단일 table/index build 폭탄을 제거하기 위한 schema 전환 기록

## 변경 기준

- table 계약은 유지합니다: application SQL은 기존 `transaction_read_model`, `transaction_read_model_archive` 이름을 그대로 사용합니다.
- partition 단위는 UTC 월 경계입니다.
- migration 시 기존 data min/max와 현재 월 기준 window를 포함하는 월 partition을 생성하고, 예외 값은 default partition으로 받습니다.
- primary key는 `(id, booked_at)`입니다. partition key는 포함하되, 조회 planner가 `(booked_at, id)` PK를 account cursor index 대신 고르는 것을 피합니다.
- read path 핵심 index는 parent 기준으로 유지합니다.
  - `idx_transaction_read_model_account_cursor`
  - `idx_transaction_read_model_account_status_cursor`
  - `idx_transaction_read_model_account_reference_cursor`
  - `idx_transaction_read_model_archive_account_cursor`
  - `idx_transaction_read_model_archive_account_status_cursor`
  - `idx_transaction_read_model_archive_account_reference_cursor`

## Retention 기준

- archive insert conflict 후 hot row 삭제 여부는 `ledger_entry_id` 단독이 아니라 `ledger_entry_id + booked_at`으로 판단합니다.
- hot table delete도 `id + booked_at` 조건을 같이 사용합니다.
- 이유: partitioned unique constraint는 partition key를 포함해야 하므로, 잘못된 다른 월 archive row가 동일 ledger id만으로 hot row 삭제를 허용하면 안 됩니다.

## Local 100m Seed 영향

- `tools/test/seed-transaction-read-model-100m.sh`는 explicit id insert를 유지하되 `ON CONFLICT (id, booked_at)`으로 partitioned primary key와 맞춥니다.
- FK trigger disable/enable은 parent만이 아니라 `pg_partition_tree()` 전체에 적용합니다.
- `SEED_INDEX_STRATEGY=required` 경로는 account cursor index가 있는 상태에서 적재해 t3.micro의 사후 대형 index build OOM을 피합니다.

## 검증

- RED: partition parent 부재 확인
- RED: archive conflict가 다른 `booked_at`의 ledger match만으로 hot row를 삭제하는지 확인
- GREEN: `JdbcTransactionReadRepositoryPartitionFitIntegrationTest`
- GREEN: `JdbcTransactionArchiveReadRepositoryIntegrationTest`
- GREEN: `JdbcTransactionArchiveReadRepositoryBaselineIntegrationTest`
- GREEN: `JdbcTransactionReadModelRetentionCleanupRepositoryIntegrationTest`
- GREEN: commit hook `./back/gradlew -p back check`

## Follow-up

- `#351`에서 lifecycle 자동화를 분리합니다.
- 다음 달 partition 선생성, 오래된 partition detach/archive/drop, partition별 analyze/vacuum 정책을 자동화해야 합니다.
