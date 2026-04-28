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

필수 key:

- `OCI_A1_SSH_HOST`
- `OCI_A1_SSH_PRIVATE_KEY_B64`
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

- `OCI_A1_SSH_USER`: 기본 `ubuntu`
- `OCI_A1_SSH_PORT`: 기본 `22`
- `OCI_A1_SSH_KNOWN_HOSTS_B64`: 없으면 workflow가 `ssh-keyscan`으로 수집
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
