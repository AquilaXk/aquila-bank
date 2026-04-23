# Delivery Flow

## Branch Strategy

- feature, fix, ci 작업은 `main`에서 짧게 분기한 `feature/*`, `fix/*`, `ci/*` 브랜치에서 진행합니다.
- 모든 변경은 GitHub issue를 먼저 만들고 `main` 대상 PR로만 병합합니다.
- 보호 브랜치 직접 push는 금지하고, 리뷰 가능한 한 가지 목적 단위로 PR을 유지합니다.

## GitHub Connector Fallback

- GitHub issue/PR write에서 `403 Resource not accessible by integration`가 나오면 flaky 재시도보다 connector 권한 차이를 먼저 의심합니다.
- 이 저장소에서는 같은 세션에서 write 성공이 확인된 connector를 표준 경로로 계속 사용합니다.
- 표준 경로가 아닌 connector에서 403이 나면 blind retry 없이 즉시 표준 write 경로로 fallback 합니다.
- 표준 write 경로도 실패하면 작업을 멈추고 권한 또는 사용할 connector를 확인합니다.

## CI Gate

- backend/frontend CI는 `main` 대상 PR에서 실행합니다.
- workflow 수정만 있는 PR도 gate가 돌도록 `.github/workflows/**` 변경을 CI path에 포함합니다.

## Main CI

- `main`에 merge된 SHA는 `Main CI` workflow가 backend/frontend check를 다시 실행합니다.
- `Main CI`가 push 이벤트에서 성공하면 `Staging Deploy` workflow가 같은 SHA를 staging에 배포합니다.
- `Staging Deploy`는 `github.event.workflow_run.head_sha`를 deploy SHA로 고정하고, 현재 `origin/main`과 같은지 검증합니다.
- 오래된 `Main CI` 완료가 뒤늦게 도착하면 deploy step을 skip해 staging 역배포와 불필요한 failure를 같이 막습니다.

## Staging Deploy

- GitHub Environment: `staging`
- concurrency group: `staging-deploy`
- actual deploy required environment secrets:
  - `STAGING_DEPLOY_WEBHOOK_URL`
  - `STAGING_DEPLOY_TOKEN`
- post-deploy smoke required environment secrets:
  - `STAGING_BASE_URL`
  - `STAGING_SMOKE_READ_PATH`
  - `STAGING_SMOKE_WRITE_PATH`
- post-deploy smoke optional environment secrets:
  - `STAGING_SMOKE_HEALTH_PATH` (default: `/actuator/health`)
  - `STAGING_SMOKE_WRITE_METHOD` (default: `POST`)
  - `STAGING_SMOKE_WRITE_BODY` (default: `{}`)
  - `STAGING_SMOKE_AUTH_HEADER_NAME`
  - `STAGING_SMOKE_AUTH_HEADER_VALUE`
- transaction replay gate required environment secrets:
  - `STAGING_REPLAY_TOKEN`
  - `STAGING_RDS_DATABASE_URL`
  - `STAGING_REPLAY_HOT_ACCOUNT_ID`
  - `STAGING_REPLAY_HOT_FROM`
  - `STAGING_REPLAY_HOT_TO`
  - `STAGING_REPLAY_COLD_ACCOUNT_ID`
  - `STAGING_REPLAY_COLD_FROM`
  - `STAGING_REPLAY_COLD_TO`
- transaction replay gate optional environment secrets:
  - `STAGING_REPLAY_ITERATIONS` (default: `40`)
  - `STAGING_REPLAY_PAGE_LIMIT` (default: `50`)
  - `STAGING_REPLAY_REQUEST_TIMEOUT_SECONDS` (default: `5`)
  - `STAGING_REPLAY_EXPECTED_TOTAL_ROWS` (default: `100000000`)
  - `STAGING_REPLAY_HOT_P95_THRESHOLD_MS` (default: `350`)
  - `STAGING_REPLAY_COLD_P95_THRESHOLD_MS` (default: `750`)
  - `STAGING_REPLAY_STATS_MAX_AGE_HOURS` (default: `24`)
  - `STAGING_REPLAY_STATS_MAX_MODIFIED_RATIO` (default: `0.05`)
- rollback hook optional environment secrets:
  - `STAGING_ROLLBACK_WEBHOOK_URL`
  - `STAGING_ROLLBACK_TOKEN`
- deploy hook payload:
  - `sha`: staging에 배포할 main SHA
  - `repository`: `owner/repo`
  - `environment`: `staging`
  - `runUrl`: GitHub Actions run URL
- deploy hook secret이 없으면 workflow는 no-op skip으로 끝내고 staging GitHub deployment status를 만들지 않습니다.
- workflow는 실제 hook 호출 후 post-deploy smoke를 통과한 경우에만 staging GitHub deployment status를 `success`로 갱신합니다.
- transaction replay gate는 post-deploy smoke 뒤에 실행되며, `pg_class.reltuples` 검증 전에 planner stats freshness guard로 stale stats를 차단합니다.
- replay gate가 실패하면 staging deployment status는 `success`로 올라가지 않으므로 production promotion guard가 같은 SHA를 자동으로 거부합니다.
- post-deploy smoke는 health/read/write endpoint를 호출하며, read/write path는 환경별 smoke 전용 endpoint를 secret으로 주입합니다.
- smoke 또는 deploy hook 실패 시 deployment status는 `failure`가 되고, rollback hook이 설정된 경우 `sha`, `repository`, `runUrl`, `deploymentId`와 함께 호출합니다.
- production 승격은 staging deployment status가 `success`인 같은 SHA만 대상으로 삼습니다.
- rollback은 `main` 기준 revert PR을 merge해 새 staging SHA를 배포하거나, 운영자가 직전 staging 성공 SHA를 확인해 별도 재배포 절차로 진행합니다.

## Feature Flag

- 미완성 기능은 장기 `develop` 브랜치 대신 feature flag로 숨깁니다.
- 프런트 기준선은 `NEXT_PUBLIC_FEATURE_FLAGS` 환경변수로 관리합니다.
- 값은 쉼표 구분 flag key 목록입니다.
- 예시: `NEXT_PUBLIC_FEATURE_FLAGS=ops-console-preview,transfer-reversal`
