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
- EC2 legacy deploy is manual-only. `Main CI` 성공 이벤트에는 연결하지 않고, OCI A1 staging deploy만 자동 실행합니다.

## Staging Deploy

- GitHub Environment: `staging`
- concurrency group: `staging-deploy`
- deploy target runtime: `oci-a1`
- database baseline: `oci-a1-postgres-100m`
- image registry: GHCR `aquila-bank-backend`, `aquila-bank-frontend`
- required environment secret:
  - `OCI_A1_STAGING_ENV`
- deploy flow:
  - `OCI_A1_STAGING_ENV`를 shell env 파일로 로드한다.
  - `Main CI`가 성공한 main SHA를 checkout한다.
  - backend/frontend image를 `${DEPLOY_SHA:0:12}`와 `main-latest` tag로 GHCR에 push한다.
  - workflow가 OCI A1 VM에 SSH로 접속해 `ops/deploy/oci/bluegreen-deploy.sh`를 실행한다.
  - deploy script는 green backend/frontend container health가 모두 200일 때만 Nginx config를 교체하고 reload한다.
  - Nginx config 검증 또는 reload가 실패하면 이전 config와 blue slot을 유지한다.
- required OCI deploy secret이 없으면 workflow는 fail-fast하며 staging GitHub deployment status를 성공으로 만들지 않습니다.
- workflow는 OCI A1 SSH deploy와 post-deploy smoke를 통과한 경우에만 staging GitHub deployment status를 `success`로 갱신합니다.
- transaction replay gate는 post-deploy smoke 뒤에 실행되며, `pg_class.reltuples` 검증 전에 planner stats freshness guard로 stale stats를 차단합니다.
- fixture principal bootstrap은 smoke/replay 전에 `STAGING_REPLAY_USER_ID`와 hot/cold 계좌 membership을 idempotent하게 보장해 fixture JWT 403을 차단합니다.
- replay gate가 실패하면 staging deployment status는 `success`로 올라가지 않으므로 production promotion guard가 같은 SHA를 자동으로 거부합니다.
- post-deploy smoke는 health/read/write endpoint를 호출하며, read/write path는 환경별 smoke 전용 endpoint를 secret으로 주입합니다.
- smoke 또는 OCI deploy 실패 시 deployment status는 `failure`가 되고, rollback hook이 설정된 경우 `sha`, `repository`, `runUrl`, `deploymentId`와 함께 호출합니다.
- production 승격은 staging deployment status가 `success`인 같은 SHA만 대상으로 삼습니다.
- rollback은 `main` 기준 revert PR을 merge해 새 staging SHA를 배포하거나, 운영자가 직전 staging 성공 SHA를 확인해 별도 재배포 절차로 진행합니다.

### OCI A1 Staging Env 예시

`OCI_A1_STAGING_ENV` 하나에 staging CD, smoke, replay, alertmanager 값을 모두 넣습니다. 저장소에 커밋하지 않습니다. 값에 공백, `#`, `&`가 있으면 shell env 문법에 맞게 quote합니다. multi-line 값은 base64로 넣습니다.

```env
OCI_A1_SSH_HOST=146.56.149.120
OCI_A1_SSH_USER=ubuntu
OCI_A1_SSH_PORT=22
OCI_A1_SSH_PRIVATE_KEY_B64=REPLACE_BASE64_PRIVATE_KEY
OCI_A1_SSH_KNOWN_HOSTS_B64=REPLACE_BASE64_SSH_KEYSCAN_OUTPUT
OCI_A1_BACKEND_ENV_B64=REPLACE_BASE64_BACKEND_ENV
OCI_A1_FRONTEND_ENV_B64=

STAGING_BASE_URL=http://146.56.149.120
STAGING_PUBLIC_API_BASE_URL=http://146.56.149.120
STAGING_SMOKE_HEALTH_PATH=/actuator/health
STAGING_SMOKE_READ_PATH='/api/v1/transactions?accountId=910000001&from=2026-04-01T00%3A00%3A00Z&to=2026-04-30T00%3A00%3A00Z&limit=1'
STAGING_SMOKE_WRITE_PATH=/actuator/health
STAGING_SMOKE_WRITE_METHOD=GET
STAGING_SMOKE_WRITE_BODY={}
STAGING_SMOKE_AUTH_HEADER_NAME=Authorization
STAGING_SMOKE_AUTH_HEADER_VALUE='Bearer REPLACE_FIXTURE_TOKEN'

STAGING_REPLAY_TOKEN=REPLACE_FIXTURE_TOKEN
STAGING_OCI_A1_DATABASE_URL='postgresql://aquila:<password>@127.0.0.1:5432/aquila'
STAGING_REPLAY_USER_ID=55
STAGING_REPLAY_LOGIN_ID=staging-fixture-user
STAGING_REPLAY_USER_PASSWORD_HASH=staging-fixture-password-hash-not-for-login
STAGING_REPLAY_USER_DISPLAY_NAME='Staging Fixture User'
STAGING_REPLAY_HOT_ACCOUNT_ID=910000001
STAGING_REPLAY_HOT_ACCOUNT_NUMBER=STG-HOT-910000001
STAGING_REPLAY_HOT_FROM=2026-04-01T00:00:00Z
STAGING_REPLAY_HOT_TO=2026-04-30T00:00:00Z
STAGING_REPLAY_COLD_ACCOUNT_ID=910000002
STAGING_REPLAY_COLD_ACCOUNT_NUMBER=STG-COLD-910000002
STAGING_REPLAY_COLD_FROM=2026-01-01T00:00:00Z
STAGING_REPLAY_COLD_TO=2026-01-31T00:00:00Z
STAGING_REPLAY_ITERATIONS=40
STAGING_REPLAY_PAGE_LIMIT=50
STAGING_REPLAY_REQUEST_TIMEOUT_SECONDS=5
STAGING_REPLAY_EXPECTED_TOTAL_ROWS=100000000
STAGING_REPLAY_HOT_P95_THRESHOLD_MS=350
STAGING_REPLAY_COLD_P95_THRESHOLD_MS=750
STAGING_REPLAY_STATS_MAX_AGE_HOURS=24
STAGING_REPLAY_STATS_MAX_MODIFIED_RATIO=0.05

ALERTMANAGER_RECEIVER_WEBHOOK_ENABLED=true
ALERTMANAGER_RECEIVER_WEBHOOK_URL='https://example.invalid/alertmanager'
ALERTMANAGER_RECEIVER_SLACK_ENABLED=false
ALERTMANAGER_RECEIVER_PAGERDUTY_ENABLED=false
```

`OCI_A1_BACKEND_ENV_B64`는 backend container `.env` 본문을 base64 인코딩한 값입니다.

```env
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/aquila
SPRING_DATASOURCE_USERNAME=aquila
SPRING_DATASOURCE_PASSWORD=<secret>
SECURITY_JWT_SECRET=<secret>
DB_POOL_MAX_SIZE=4
SERVER_THREADS_MAX=16
OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=3
NOTIFICATION_SSE_MAX_TOTAL_SESSIONS=64
```

예시 생성:

```bash
base64 -w0 ~/.ssh/oci_a1_deploy
ssh-keyscan -p 22 -H 146.56.149.120 | base64 -w0
base64 -w0 .env.backend-staging
```

## Feature Flag

- 미완성 기능은 장기 `develop` 브랜치 대신 feature flag로 숨깁니다.
- 프런트 기준선은 `NEXT_PUBLIC_FEATURE_FLAGS` 환경변수로 관리합니다.
- 값은 쉼표 구분 flag key 목록입니다.
- 예시: `NEXT_PUBLIC_FEATURE_FLAGS=ops-console-preview,transfer-reversal`
