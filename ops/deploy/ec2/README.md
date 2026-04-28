# EC2 Blue/Green Deploy

이 경로는 AWS App EC2 한 대에서 Docker + Nginx blue/green 배포를 수행한다.

## Runtime

- entrypoint: `aquila-bank-nginx` on host port `80`
- backend slots: `aquila-bank-backend-a`, `aquila-bank-backend-b`
- frontend slots: `aquila-bank-front-a`, `aquila-bank-front-b`
- Docker network: `aquila-bank-prod`
- deploy root: `/opt/aquila-bank`

## Database Boundary

1억 건 PostgreSQL fixture는 EC2로 옮기지 않는다. Backend container는 `EC2_BACKEND_ENV` secret으로 주입되는 DB 접속 설정을 사용한다. 로컬 Mac Docker PostgreSQL을 EC2에서 사용하려면 reverse SSH tunnel 또는 VPN을 별도 보안 작업으로 구성한다.

Backend/frontend container에는 `host.docker.internal`이 EC2 Docker host gateway로 잡힌다. 로컬 Mac DB를 tunnel로 붙일 때는 EC2 host가 접근할 수 있는 포트로 먼저 고정한 뒤, `EC2_BACKEND_ENV`의 JDBC URL에서 해당 host/port를 사용한다.

## Required GitHub Secrets

- `AWS_REGION`
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `EC2_INSTANCE_TAG_NAME`: 기본 Terraform 값은 `aquila-bank-baseline-ec2`
- `EC2_BACKEND_ENV`: backend container에 넣을 `.env` 본문

Optional:

- `EC2_FRONTEND_ENV`: frontend runtime `.env` 본문
- `EC2_PUBLIC_API_BASE_URL`: frontend build arg `NEXT_PUBLIC_API_BASE_URL`
- `EC2_PUBLIC_BASE_URL`: deploy 후 smoke URL

## Failure Behavior

Green 슬롯 헬스체크나 Nginx reload가 실패하면 green container를 제거하고 기존 blue 슬롯과 기존 Nginx 설정을 유지한다.
