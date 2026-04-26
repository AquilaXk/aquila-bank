# Aquila Bank

대용량 트래픽을 작은 인프라에서 무제한 처리하는 대신, EC2 `t3.micro` + RDS `db.t4g.small` + gp3 기준으로 과부하를 제한하고 1억 건 규모 거래 데이터를 bounded query로 조회하는 것을 목표로 하는 웹뱅킹 프로젝트입니다.

## Overview

- 목표:
  - 대용량 트래픽 방어와 과부하 시 fail-fast
  - 실시간 알림 지원
  - 1억 건 규모 거래 데이터의 계좌/기간/keyset page 조회 지원
  - 제한된 인프라(EC2 `t3.micro` + RDS `db.t4g.small` + gp3)에서도 운영 가능한 구조 지향
  - 로컬/배포 환경 모두 `PostgreSQL 18` 표준화
- 제외 목표:
  - 전체 1억 건 검색/집계/정렬
  - Kafka, Prometheus, Grafana의 같은 host 상시 필수 운영
  - 작은 인프라 한계를 넘는 무제한 SSE/API 동시성
- 원칙:
  - 헥사고날 아키텍처 준수
  - 도메인과 인프라 경계 분리
  - bounded query, 동시성 제한, 정합성, 운영 단순성 우선

## Project Architecture Structure

```text
.
├── front
├── back
├── ops
├── .github
├── compose.yml
├── compose.t3micro.yml
└── compose.loadtest.yml
```

- `front`: 고객/운영 웹 애플리케이션
- `back`: API, 도메인, 배치, 어댑터
- `ops`: reverse proxy 같은 운영 baseline 파일
- `.github`: 이슈/PR 템플릿과 협업 메타 설정
- `compose.yml`: 로컬 개발용 Docker Compose 인프라 실행 기준
- `compose.t3micro.yml`: 로컬 인프라를 작은 CPU/메모리 budget으로 띄우는 t3.micro 근사 override
- `compose.loadtest.yml`: k6, Prometheus, Grafana 기반 HTTP 부하 테스트 runtime overlay

## Runtime Baseline

- database: `PostgreSQL 18`
- local environment: `Docker Compose + PostgreSQL 18`
- deployed environment: `EC2 t3.micro + RDS PostgreSQL 18 db.t4g.small + gp3`

## Environment Split

- 로컬 개발: `Docker Compose + PostgreSQL 18`
- Kafka 로컬 검증: `docker compose --profile kafka up`로 명시적으로 opt-in 합니다.
- 로컬 t3.micro 근사 검증: `compose.t3micro.yml`과 `tools/test/run-docker-t3micro-capacity-smoke.sh`로 CPU/메모리 cgroup 제한을 적용합니다.
- 로컬 HTTP 부하 테스트: `compose.loadtest.yml`로 backend, k6, Prometheus, Grafana, Alertmanager, Postgres exporter를 함께 띄웁니다. Prometheus/Grafana는 부하테스트/선택 운영 자산이며 같은 t3.micro host 상시 필수 구성에서 제외합니다.
- 로컬 1억 건 synthetic 조회 테스트: dataset 생성은 `tools/test/prepare-transaction-read-model-100m-fixture.sh`, t3.micro 조회 부하는 `tools/test/run-transaction-read-model-100m-k6-local.sh --k6-only`로 분리합니다. 생성 phase는 별도 PostgreSQL budget을 사용하고, 조회 phase만 t3.micro budget으로 제한합니다.
- 배포 환경: `EC2 t3.micro + RDS PostgreSQL 18 db.t4g.small + gp3`
- EC2 reverse proxy baseline template은 [ops/nginx/nginx.conf](/Users/aquila/Custom/GitProjects/aquila-bank/ops/nginx/nginx.conf)에 두고, runtime 값은 `ops/nginx/runtime.env.example` 기반으로 렌더링합니다.
- `compose.yml`은 로컬 개발 전용이며, 배포용 인프라 정의는 포함하지 않습니다.
- Docker 근사 검증은 AWS `t3.micro`의 CPU credit, EBS 지연, 실제 네트워크를 재현하지 못하므로 최종 120% headroom 판정은 EC2 `t3.micro` staging smoke 결과로 닫습니다.
- 성능 테스트 결과는 [docs/performance-results](/Users/aquila/Custom/GitProjects/aquila-bank/docs/performance-results/README.md)에 Markdown으로 남깁니다.

## Delivery Flow

- feature 작업은 `main`에서 짧게 분기한 `feat/*`, `fix/*`, `perf/*`, `chore/*`, `build/*`, `docs/*` 브랜치에서 진행합니다.
- PR 리뷰와 backend/frontend CI 통과 후 `main`에 병합합니다.
- `main`에 병합되면 `Main CI` workflow가 backend/frontend check를 다시 실행하고, 같은 SHA를 `Staging Deploy` workflow로 전달합니다.
- staging 배포는 현재 `origin/main` SHA와 일치하는 `Main CI` 성공 SHA만 진행하며, deploy hook secret이 없으면 no-op으로 종료합니다.
- production 승격은 staging deployment status가 `success`인 같은 SHA만 대상으로 하고, GitHub Environment 수동 승인 또는 `prod-*` tag로만 진행합니다.
- 미완성 기능은 장기 `develop` 브랜치 대신 feature flag로 기본 비노출 처리합니다.
- 상세 운영 규칙은 [docs/delivery-flow.md](/Users/aquila/Custom/GitProjects/aquila-bank/docs/delivery-flow.md)에서 확인합니다.

## Backend Package Structure

```text
com.aquilabank
├── domain
├── global
└── standard
    └── util
```

- `domain`: 핵심 도메인, 유스케이스, 포트
- `global`: 설정, 보안, 웹, 영속성, 예외 처리 등 인프라/어댑터
- `standard/util`: 공통 기준과 최소 유틸리티

## Domain Structure

- `auth`: 로그인, 세션, MFA, 권한
- `account`: 고객 계좌, 잔액, 상태
- `transaction`: 대량 거래 조회, 검색, 상세 응답
- `ledger`: 거래 정합성, 원장 기록, 감사 추적
- `notification`: 실시간 이벤트, 읽음 처리, 재시도
- `ops`: 모니터링, 운영 배치, 장애 대응

## Architecture Principle

- domain은 framework, web, persistence 구현체에 의존하지 않습니다.
- global은 domain을 사용해 어댑터와 설정을 구성합니다.
- util에는 비즈니스 로직을 두지 않고, 공통 기술 보조 코드만 둡니다.
- 읽기 경로는 대용량 트래픽 방어, 1억 건 저장 규모, `t3.micro` 운영 한계를 함께 고려해 경량화와 분리를 우선합니다.
- 거래 목록 조회는 `accountId + 기간 + keyset pagination` 경로만 온라인 목표로 둡니다.
- 전체 1억 건 검색/집계/정렬은 온라인 목표에서 제외합니다.

## Outbox Dispatcher

- `OUTBOX_POLLER_BATCH_SIZE`: 정상 상태 claim batch 상한입니다.
- `OUTBOX_POLLER_ADAPTIVE_ENABLED`: Kafka/DB 지연 시 adaptive batch/backoff 적용 여부입니다.
- `OUTBOX_POLLER_MIN_BATCH_SIZE`: publish 실패가 이어질 때 줄일 최소 batch 크기입니다.
- `OUTBOX_POLLER_MAX_ADAPTIVE_DELAY_MS`: 실패 또는 빈 poll 반복 시 추가 대기 시간 상한입니다.
- 운영 복구 시 `OUTBOX_POLLER_ADAPTIVE_ENABLED=false`로 고정 batch/주기 모드로 되돌릴 수 있습니다.

## Provider Delivery

- notification EMAIL/SMS provider delivery는 `notification_channel_outbox`와 `NotificationChannelProviderPort`를 통해 외부 호출을 worker로 분리합니다.
- `NOTIFICATION_CHANNEL_PROVIDER_WORKER_ENABLED`: notification provider worker 활성화 여부입니다. 기본값은 `false`입니다.
- `NOTIFICATION_CHANNEL_PROVIDER_DELIVERY_ENABLED=true`이면 channel별 webhook adapter가 활성화되고, `false`이면 외부 secret 없는 logging adapter fallback을 사용합니다.
- notification provider destination은 `bank_user_verified_contact`의 `user_id + contact_channel` 기준 `provider_destination`을 사용합니다.
- password recovery provider delivery는 token 발급 transaction 안에서 `auth_password_recovery_delivery_outbox`를 적재하고, worker가 EMAIL 우선/SMS fallback verified contact snapshot으로 webhook adapter를 호출합니다.
- `AUTH_PASSWORD_RECOVERY_DELIVERY_ENABLED=true`이고 `AUTH_PASSWORD_RECOVERY_DELIVERY_EMAIL_URL` 또는 `AUTH_PASSWORD_RECOVERY_DELIVERY_SMS_URL`가 있으면 password recovery webhook adapter가 provider endpoint로 `POST` 합니다.
- verified contact 누락, 비활성 사용자, channel URL 누락, 이미 사용/만료/대체된 password recovery token은 외부 오발송 방지를 위해 fail-safe skip 처리합니다.
- webhook timeout, 4xx/5xx, network error 같은 실제 provider 장애만 bounded retry/backoff/quarantine 흐름으로 들어갑니다.
