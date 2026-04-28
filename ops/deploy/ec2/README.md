# EC2 Blue/Green Deploy

이 경로는 AWS App EC2 한 대에서 Docker + Nginx blue/green 배포 smoke를 수행한다. 현재 remote DB/capacity baseline은 OCI A1 Flex 4 OCPU / 24GB + data 300GB self-managed PostgreSQL이며, EC2 경로는 legacy/optional app smoke로만 유지한다.

## Runtime

- entrypoint: `aquila-bank-nginx` on host port `80`
- backend slots: `aquila-bank-backend-a`, `aquila-bank-backend-b`
- frontend slots: `aquila-bank-front-a`, `aquila-bank-front-b`
- Docker network: `aquila-bank-prod`
- deploy root: `/opt/aquila-bank`

## Database Boundary

1억 건 PostgreSQL fixture는 EC2로 옮기지 않는다. Backend container는 `EC2_BACKEND_ENV` secret으로 주입되는 DB 접속 설정을 사용한다. 로컬 Mac Docker PostgreSQL 또는 OCI A1 PostgreSQL을 EC2에서 사용하려면 reverse SSH tunnel 또는 VPN을 별도 보안 작업으로 구성한다.

Backend/frontend container에는 `host.docker.internal`이 EC2 Docker host gateway로 잡힌다. 로컬 Mac DB를 tunnel로 붙일 때는 EC2 host가 접근할 수 있는 포트로 먼저 고정한 뒤, `EC2_BACKEND_ENV`의 JDBC URL에서 해당 host/port를 사용한다.

## Backend Proxy Contract

Nginx는 public Host를 backend에 그대로 넘기지 않는다. backend location은 `Host`를 hyphen 기반 backend slot 또는 `NGINX_BACKEND_PROXY_HOST` 값으로 고정하고, 원래 public host는 `X-Forwarded-Host`로 전달한다. 이 계약은 Tomcat/Servlet Host validation이 public domain이나 underscore upstream name 때문에 400을 반환하지 않게 하기 위한 것이다.

배포 workflow의 external smoke는 `EC2_PUBLIC_BASE_URL`이 있을 때 다음을 확인한다.

- backend health: `EC2_PUBLIC_BACKEND_HEALTH_PATH`, 기본 `/actuator/health`
- backend API: `EC2_PUBLIC_API_SMOKE_PATH`, 기본 transaction read query 1건
- API expected status: `EC2_PUBLIC_API_SMOKE_EXPECTED_STATUS`, 기본 `401`

기본 API smoke는 인증 토큰 없이 401을 기대해 backend/nginx 경로가 400/5xx로 막히는지만 조기에 잡는다. 실제 200 smoke가 필요하면 `EC2_PUBLIC_API_SMOKE_AUTH_HEADER_NAME=Authorization`, `EC2_PUBLIC_API_SMOKE_AUTH_HEADER_VALUE=Bearer <fixture-jwt>`와 `EC2_PUBLIC_API_SMOKE_EXPECTED_STATUS=200`을 staging secret으로 넣는다.

## Local DB Capacity Smoke

EC2 App + 외부 DB 1억 건 부하 테스트는 legacy 진단 경로다. 저장소에 secret을 남기지 않고 local-only env 파일로 실행한다. 현재 권장 remote DB/capacity 경로는 OCI A1 4 OCPU / 24GB + data 300GB이다.

```bash
tools/test/run-ec2-local-db-capacity-env-doctor.sh --print-env-template > .env/ec2-local-db-capacity.env
```

`.env/ec2-local-db-capacity.env`에는 다음 값이 필요하다.

- `EC2_DIRECT_BACKEND_BASE_URL`: EC2 backend direct URL. EC2 host에서 실행하면 보통 `http://127.0.0.1:18080`
- `EC2_NGINX_BASE_URL`: Nginx public 또는 host-local URL
- `EC2_LOADTEST_AUTH_TOKEN`: prod profile에서 사용할 fixture JWT 또는 제한된 loadtest profile token
- `EC2_LOCAL_DB_HOST=host.docker.internal`
- `EC2_LOCAL_DB_PORT=25432`
- `EC2_LOCAL_DB_NAME`, `EC2_LOCAL_DB_USER`, `EC2_LOCAL_DB_PASSWORD`

실행 전 doctor로 container network에서 host-gateway DB tunnel까지 확인한다.

```bash
EC2_LOCAL_DB_CAPACITY_ENV_FILE=.env/ec2-local-db-capacity.env \
  tools/test/run-ec2-local-db-capacity-env-doctor.sh
```

runner는 같은 `EC2_CAPACITY_RUN_ID`를 공유해 direct/nginx/resource/comparison artifact를 묶는다.

```bash
EC2_LOCAL_DB_CAPACITY_ENV_FILE=.env/ec2-local-db-capacity.env \
  tools/test/run-ec2-direct-backend-100m-k6-smoke.sh

EC2_LOCAL_DB_CAPACITY_ENV_FILE=.env/ec2-local-db-capacity.env \
  tools/test/run-ec2-nginx-100m-k6-smoke.sh

EC2_LOCAL_DB_CAPACITY_ENV_FILE=.env/ec2-local-db-capacity.env \
  tools/test/run-ec2-local-db-307-burst-gate.sh
```

리소스 증거와 direct-vs-nginx 비교는 별도 artifact로 남긴다.

```bash
EC2_LOCAL_DB_CAPACITY_ENV_FILE=.env/ec2-local-db-capacity.env \
  tools/test/run-ec2-local-db-resource-snapshot.sh

EC2_DIRECT_K6_SUMMARY_MD=build/reports/k6/<run>-direct-backend-summary.md \
EC2_NGINX_K6_SUMMARY_MD=build/reports/k6/<run>-nginx-summary.md \
  tools/test/compare-ec2-direct-vs-nginx-latency.sh
```

`EC2_RESOURCE_CLOUDWATCH_ENABLED=true`를 켜면 `EC2_CAPACITY_AWS_REGION`, `EC2_CAPACITY_EC2_INSTANCE_ID`, `EC2_CAPACITY_EBS_VOLUME_ID`, `EC2_CAPACITY_CLOUDWATCH_START_TIME`, `EC2_CAPACITY_CLOUDWATCH_END_TIME`를 함께 지정한다. CloudWatch는 EC2 CPU credit, CPU utilization, EBS queue/ops만 수집하며 RDS 지표는 이 app-only 경로에서 사용하지 않는다. OCI A1 baseline 리소스는 이 EC2 CloudWatch 수집 대상이 아니다.

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
- `EC2_PUBLIC_API_SMOKE_AUTH_HEADER_NAME`, `EC2_PUBLIC_API_SMOKE_AUTH_HEADER_VALUE`: backend API smoke용 인증 header
- `EC2_PUBLIC_API_SMOKE_EXPECTED_STATUS`: 인증 없는 기본 smoke는 `401`, fixture JWT smoke는 `200`

## Failure Behavior

Green 슬롯 헬스체크나 Nginx reload가 실패하면 green container를 제거하고 기존 blue 슬롯과 기존 Nginx 설정을 유지한다.
