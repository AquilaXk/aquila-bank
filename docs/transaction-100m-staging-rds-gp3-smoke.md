# Transaction 100m Staging RDS gp3 Smoke

RDS db.t4g.small + gp3 staging 환경에서 100m transaction read path를 read-only로 확인하는 절차입니다. Docker local smoke는 RDS recovery, gp3 IO, CPU credit, network latency를 재현하지 못하므로 staging smoke는 별도 runner로 분리합니다.

## Guard

- staging backend URL은 `https://`를 기본으로 요구합니다.
- `localhost`, `127.0.0.1`, `0.0.0.0`, `::1` URL은 기본 차단합니다.
- 실행 전 `STAGING_RDS_CONFIRM=read-only-staging-rds`를 요구합니다.
- RDS 접속 secret, 운영 URL, bearer token은 저장소와 결과 문서에 기록하지 않습니다.
- runner는 k6 read scenario만 실행하며 restore, cleanup, write traffic을 수행하지 않습니다.

## Dry Run

```bash
STAGING_RDS_BASE_URL=https://staging.example.invalid \
STAGING_RDS_HOT_ACCOUNT_ID=910000001 \
STAGING_RDS_HOT_FROM=2026-04-01T00:00:00Z \
STAGING_RDS_HOT_TO=2026-04-30T00:00:00Z \
STAGING_RDS_COLD_ACCOUNT_ID=910000002 \
STAGING_RDS_COLD_FROM=2026-01-01T00:00:00Z \
STAGING_RDS_COLD_TO=2026-01-31T00:00:00Z \
tools/test/run-transaction-100m-staging-rds-gp3-smoke.sh --dry-run
```

## Run

```bash
STAGING_RDS_CONFIRM=read-only-staging-rds \
STAGING_RDS_BASE_URL=<staging-backend-url> \
STAGING_RDS_AUTH_TOKEN=<token-from-secret-store> \
STAGING_RDS_HOT_ACCOUNT_ID=<hot-account-id> \
STAGING_RDS_HOT_FROM=<from> \
STAGING_RDS_HOT_TO=<to> \
STAGING_RDS_COLD_ACCOUNT_ID=<cold-account-id> \
STAGING_RDS_COLD_FROM=<from> \
STAGING_RDS_COLD_TO=<to> \
STAGING_RDS_VUS=4 \
STAGING_RDS_DURATION=2m \
tools/test/run-transaction-100m-staging-rds-gp3-smoke.sh
```

## Result

기본값은 `STAGING_RDS_ARCHIVE_RESULTS=true`입니다. k6 summary는 `build/reports/k6`에 생성되고 Markdown 사본은 `docs/performance-results`에 보관됩니다.

확인값:

- hot/cold p95, p99, max latency
- transaction 429 rate
- failed request rate
- RDS CPU credit, FreeableMemory, ReadIOPS/WriteIOPS, queue depth
- backend CPU/memory와 DB pool pending 여부

## Rollback

이 smoke는 read-only traffic만 발생시키므로 application rollback은 필요하지 않습니다. staging latency나 RDS credit 소진이 보이면 실행을 중단하고 같은 SHA의 staging 배포 상태를 유지한 채 VU/duration을 낮춰 재측정합니다.
