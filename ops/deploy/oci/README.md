# OCI A1 Blue/Green Deploy

이 경로는 OCI A1 Ubuntu VM 한 대에서 Docker + Nginx blue/green staging 배포를 수행한다.

## Runtime

- host: OCI A1 Flex 4 OCPU / 24GB
- app dir: `/opt/aquila-bank`
- Docker network: `aquila-bank-prod`
- public entrypoint: `aquila-bank-nginx` on host port `80`
- backend slots: `aquila-bank-backend-a`, `aquila-bank-backend-b`
- frontend slots: `aquila-bank-front-a`, `aquila-bank-front-b`

`bluegreen-deploy.sh`는 green backend/frontend container를 먼저 시작하고, `/actuator/health`와 `/`가 200을 반환해야 Nginx config를 교체한다. Nginx config test 또는 reload가 실패하면 이전 config를 복원하고 기존 blue slot을 유지한다.

## GitHub Actions Input

`staging` Environment에는 `OCI_A1_STAGING_ENV` secret 하나만 둔다. 이 값은 shell env 파일 형식이며, multi-line 값은 base64로 넣는다.

배포 job은 OCI VM에 설치된 GitHub Actions self-hosted runner에서 실행한다. runner label은 `self-hosted`, `oci-a1-staging`을 사용하고, runner 사용자는 `bluegreen-deploy.sh`의 Docker/Nginx 작업을 위해 passwordless sudo가 필요하다. 배포는 같은 VM 안에서 실행되므로 GitHub-hosted runner의 inbound 접속이나 별도 SSH secret은 쓰지 않는다.

runner 선행 조건:

- labels: `self-hosted`, `oci-a1-staging`
- outbound: GitHub, GHCR 접근 가능
- commands: `base64`, `curl`, `jq`, `psql`
- privilege: passwordless sudo
- database: `STAGING_OCI_A1_DATABASE_URL`로 PostgreSQL fixture DB 접근 가능
- app database host: backend container는 Docker network의 `aquila-postgres:5432`로 PostgreSQL에 접근

## Database Preflight

`bluegreen-deploy.sh`는 green backend를 시작하기 전에 `OCI_A1_BACKEND_ENV_B64` 안의 `SPRING_DATASOURCE_URL` 또는 `DB_URL`을 읽고 같은 Docker network에서 `pg_isready`를 실행한다.

- DB host가 `aquila-postgres`이면 script가 green backend 시작 전에 PostgreSQL container를 준비한다.
- `aquila-postgres.service`가 설치된 paid/data-volume VM은 `/etc/aquila-postgres.env`를 backend env 기준으로 채우고 systemd service를 시작한다.
- systemd service가 없는 always-free VM은 Docker named volume `aquila-postgres-data`로 `aquila-postgres` container를 생성한다.
- 기존 `aquila-postgres` container가 있으면 새로 만들지 않고 start와 `aquila-bank-prod` network 연결만 보정한다.
- 기존 Docker volume은 삭제하거나 초기화하지 않는다.
- 자동 bootstrap을 막고 싶으면 `POSTGRES_BOOTSTRAP_ENABLED=false`를 staging env에 넣는다.
- preflight 실패 시 script는 `aquila-postgres` container 상태, network 연결 목록, PostgreSQL log tail을 출력한다.
- 로그가 `backend DB host requires PostgreSQL container`이면 DB host는 container alias를 가리키지만 자동 bootstrap이 꺼졌거나 container 시작에 실패한 상태다.
- 로그가 `backend database preflight failed`이면 container/network는 확인됐고 DB명, 사용자, 비밀번호, PostgreSQL readiness를 우선 확인한다.

최초 bootstrap은 빈 PostgreSQL 18 database만 만든다. Backend Flyway가 schema를 적용하고, 1억 건 fixture restore/검증은 배포 이후 별도 runner로 수행한다.

## Flyway Migration Gate

Backend CI는 PR에서 변경된 Flyway SQL migration만 검사해 blue/green unsafe 패턴을 차단한다. default 없는 `ADD COLUMN ... NOT NULL`, `DROP TABLE`, `DROP COLUMN`, rename, `SET NOT NULL`, type 변경, `DROP INDEX`, `TRUNCATE`는 기본 실패한다.

의도적으로 compatibility window를 닫는 예외는 migration 파일에 사유를 포함한 marker를 남긴다.

```sql
-- flyway:allow-breaking-change legacy column removed after two-phase deploy
ALTER TABLE account DROP COLUMN legacy_code;
```

로컬 확인:

```bash
tools/test/check-flyway-backward-compatible-migrations.sh --self-test
tools/test/check-flyway-backward-compatible-migrations.sh --print-plan
```

## Runner Bootstrap

OCI VM에서 GitHub self-hosted runner를 처음 등록할 때는 `bootstrap-self-hosted-runner.sh`를 1회 실행한다. `GITHUB_RUNNER_TOKEN`은 GitHub의 repository runner 등록 화면에서 발급한 단기 registration token이며 저장소나 shell history에 남기지 않는다.

```bash
GITHUB_RUNNER_TOKEN=<registration-token> \
GITHUB_REPOSITORY_SLUG=AquilaXk/aquila-bank \
RUNNER_LABELS=self-hosted,oci-a1-staging \
  ops/deploy/oci/bootstrap-self-hosted-runner.sh
```

설치 후에는 로컬 doctor나 GitHub Actions의 `OCI A1 Runner Doctor` workflow로 확인한다.

```bash
ops/deploy/oci/check-self-hosted-runner.sh
```

필수 key:

- `OCI_A1_BACKEND_ENV_B64`
- `STAGING_BASE_URL`
- `STAGING_SMOKE_READ_PATH`
- `STAGING_SMOKE_WRITE_PATH`
- `STAGING_REPLAY_TOKEN`
- `STAGING_OCI_A1_DATABASE_URL`
- `STAGING_REPLAY_HOT_ACCOUNT_ID`
- `STAGING_REPLAY_HOT_FROM`
- `STAGING_REPLAY_HOT_TO`
- `STAGING_REPLAY_COLD_ACCOUNT_ID`
- `STAGING_REPLAY_COLD_FROM`
- `STAGING_REPLAY_COLD_TO`

선택 key:

- `OCI_A1_FRONTEND_ENV_B64`
- `STAGING_PUBLIC_API_BASE_URL`: 없으면 `STAGING_BASE_URL`
- `STAGING_SMOKE_HEALTH_PATH`: 기본 `/actuator/health`
- `STAGING_SMOKE_WRITE_METHOD`: 기본 `POST`
- `STAGING_SMOKE_WRITE_BODY`: 기본 `{}`
- `STAGING_SMOKE_AUTH_HEADER_NAME`, `STAGING_SMOKE_AUTH_HEADER_VALUE`
- `STAGING_REPLAY_ITERATIONS`, `STAGING_REPLAY_PAGE_LIMIT`, replay threshold 계열
- `ALERTMANAGER_RECEIVER_*`
- `STAGING_ROLLBACK_WEBHOOK_URL`, `STAGING_ROLLBACK_TOKEN`

## Local Manual Run

수동 실행은 같은 변수명을 export한 뒤 root 권한으로 실행한다.

```bash
export BACKEND_IMAGE=ghcr.io/aquilaxk/aquila-bank-backend
export FRONTEND_IMAGE=ghcr.io/aquilaxk/aquila-bank-frontend
export IMAGE_TAG=<12-char-sha>
export GITHUB_ACTOR=<github-user>
export GITHUB_TOKEN_B64=<base64-token>
export BACKEND_ENV_B64=<base64-backend-env>
export FRONTEND_ENV_B64=<base64-frontend-env>
sudo -E ops/deploy/oci/bluegreen-deploy.sh
```

비밀값은 파일이나 shell history에 남기지 않는다. GitHub Actions 경로를 기본 운영 경로로 사용한다.
