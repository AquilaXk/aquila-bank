# Aquila Bank

작은 cloud budget 안에서 거래 정합성, 대량 조회, 실시간 알림, 배포 운영을 함께 검증하는 웹뱅킹 프로젝트입니다.
단순한 계좌/거래 CRUD보다 `1억 건 거래 read model`, `bounded query`, `outbox 기반 알림`, `OCI A1 운영 budget`, `회귀 방지 gate`에 더 큰 비중을 두고 설계했습니다.

![Aquila Bank architecture](docs/assets/readme-architecture.png)

## 프로젝트 개요

Aquila Bank는 제한된 인프라에서 무제한 트래픽을 처리하는 프로젝트가 아닙니다. OCI A1 Flex 4 OCPU / 24GB + data 200GB self-managed PostgreSQL 18 기준으로 1억 건 거래 데이터를 적재하고, 계좌/기간/keyset page처럼 bounded 된 조회만 온라인 목표로 둡니다.

- 프런트는 `Next.js 14 + React 18 + TypeScript`, 백엔드는 `Spring Boot 4 + Java 21`로 구성했습니다.
- 데이터 기준은 `PostgreSQL 18`이고, 이벤트/알림 경로는 `Kafka 4.0`과 outbox 패턴으로 분리했습니다.
- 운영 경로는 `Nginx reverse proxy`, GitHub Actions CI/CD, OCI A1 staging/production 승격 기준을 중심으로 정리했습니다.
- Prometheus/Grafana/k6는 부하테스트와 선택 관측 자산으로 두고, 같은 host 상시 필수 운영 구성으로 보지 않습니다.

## 왜 이 프로젝트가 차별점이 있는가

- `거래 정합성`을 기능 수보다 앞에 두었습니다.
  - 계좌 잔액, 원장, 감사 추적 경계를 분리하고 도메인이 framework/infrastructure 구현에 의존하지 않도록 관리합니다.
- `작은 인프라에서 버티는 읽기 경로`를 기준으로 설계했습니다.
  - 전체 1억 건 검색/집계/정렬은 온라인 목표에서 제외하고, `accountId + 기간 + keyset pagination` 경로를 중심으로 봅니다.
- `실시간 알림을 운영 가능한 비동기 경로`로 다룹니다.
  - outbox claim, Kafka publish, notification inbox consume, SSE fan-out 경로에서 재시도와 중복 방지를 분리합니다.
- `성능 주장과 운영 증거`를 분리합니다.
  - fixture pass나 harness 통과만으로 성능 개선을 주장하지 않고, OCI 또는 production-like live artifact를 기준으로 판단합니다.

## 핵심 기능

### Banking Domain

- 계좌 생성 / 상태 관리 / 잔액 조회
- 송금, 부분 취소, 한도 정책
- 거래 상세 조회와 계좌별 거래 목록 조회
- 원장 감사 추적과 snapshot/reconciliation 복구 경로

### Transaction Read Path

- 1억 건 transaction read model fixture 기준 검증
- 계좌/기간/status/cursor 조건 기반 bounded query
- keyset pagination, covering index, timeout, admission control
- archive query와 retention cutoff load test 기준 분리

### Auth & Security

- JWT access token / refresh token session
- MFA TOTP / backup code / remember device
- password recovery token과 delivery outbox
- internal admin API 인증, 감사 검색, 상태 변경 이력

### Notification & Async

- notification inbox / unread projection
- SSE stream, replay, reconnect storm 방어
- outbox dispatcher adaptive batch/backoff
- provider delivery worker와 DLQ/redrive 운영 API

### Operations

- Nginx reverse proxy baseline과 transaction-read edge gate
- GitHub Actions backend/frontend/main CI
- OCI A1 staging deploy와 production promotion gate
- k6, Prometheus, Grafana 기반 부하테스트/관측 asset

## 기술 스택

| 영역 | 스택 |
| --- | --- |
| Frontend | Next.js 14, React 18, TypeScript |
| Backend | Spring Boot 4, Java 21, Spring Security, JDBC, Flyway |
| Data | PostgreSQL 18 |
| Event / Async | Kafka 4.0, Outbox, SSE |
| Edge / Ops | Nginx, Docker Compose, OCI A1 Flex, GitHub Actions |
| Observability / Load Test | Prometheus, Grafana, k6, Postgres exporter |
| Quality | Gradle check, Spotless, JaCoCo, Testcontainers, Next lint |

## 아키텍처 포인트

### 1. Hexagonal Boundary

- `domain`은 Spring/JPA/web/sdk 구현에 의존하지 않습니다.
- inbound/outbound adapter와 persistence/web/security 설정은 `global`에 둡니다.
- 비즈니스 규칙은 `util`이 아니라 도메인 model/use case/service에 둡니다.

### 2. Bounded Read Path

- 온라인 거래 목록 조회는 `accountId + 기간 + keyset pagination` 중심으로 제한합니다.
- 1억 건 전체 검색/집계/정렬은 목표에서 제외합니다.
- timeout, DB pool, admission control, edge reject curve를 함께 봅니다.

### 3. Outbox & Notification

- 쓰기 transaction 안에서 outbox를 적재하고 worker가 외부 publish/delivery를 분리합니다.
- Kafka enabled 상태에서는 bootstrap/topic 필수값 누락을 startup 실패로 처리합니다.
- SSE는 실시간 알림을 제공하되 reconnect gap은 bounded replay와 pull API 재동기화로 다룹니다.

### 4. Evidence-Based Performance

- `[Perf]` 범위는 OCI 또는 production-like live artifact가 있을 때만 사용합니다.
- synthetic fixture pass, mock evidence, harness-only pass는 성능 개선 증거로 보지 않습니다.
- 작은 OCI A1 budget에서 CPU/메모리/IO 비용이 낮은 검증된 PostgreSQL 운영 패턴을 우선합니다.

## 프로젝트 구조

```text
.
├── front/                  # Next.js 14 / React 18 frontend
├── back/                   # Spring Boot 4 / Java 21 backend
├── infra/                  # OCI/AWS 선택형 Terraform baseline
├── ops/                    # Nginx, Prometheus, Grafana 운영 baseline
├── tools/                  # test/evidence/ops helper scripts
├── .github/                # issue/PR templates, CI/CD workflows
├── compose.yml             # 로컬 PostgreSQL/Kafka 개발 인프라
├── compose.t3micro.yml     # 작은 CPU/메모리 budget 근사 overlay
└── compose.loadtest.yml    # k6/Prometheus/Grafana 부하테스트 overlay
```

## 로컬 실행

### 사전 준비

- Java 21
- Node.js LTS
- Yarn Classic
- Docker Compose

### 1. 개발용 인프라 실행

```bash
docker compose up -d postgres kafka
```

- PostgreSQL: `localhost:5432`
- Kafka: `localhost:9092`

### 2. 백엔드 실행

```bash
./back/gradlew -p back bootRun
```

- Backend: `http://localhost:8080`
- Actuator health: `http://localhost:8080/actuator/health`
- Prometheus scrape: `http://localhost:8080/actuator/prometheus`

### 3. 프런트 실행

```bash
yarn --cwd front install
yarn --cwd front dev
```

- Front: `http://localhost:3000`

## 품질 게이트

### Backend

```bash
./back/gradlew -p back check
```

- unit/slice test, Testcontainers integration test, query plan gate, JaCoCo coverage verification, Spotless check를 포함합니다.
- 같은 워크트리에서 backend 검증을 병렬 실행할 때는 `tools/test/with-resource-lock.sh`로 직렬화합니다.

### Frontend

```bash
yarn --cwd front lint
```

### Load / Evidence

```bash
docker compose -f compose.yml -f compose.loadtest.yml --profile loadtest up -d
```

- k6, Prometheus, Grafana, Alertmanager, Postgres exporter는 부하테스트/선택 관측 용도입니다.
- 1억 건 primary evidence는 OCI A1 PostgreSQL data volume의 fixture와 cloud/off-host k6 summary로 닫습니다.

## Runtime Baseline

- database: `PostgreSQL 18`
- event broker: `Kafka 4.0` single-node KRaft broker
- primary 100m evidence: `OCI A1 Flex 4 OCPU / 24GB + self-managed PostgreSQL 18 + 200GB Block Volume`
- query/admission budget: OCI A1 `4 OCPU / 24GB` 기반 작은 CPU/메모리 budget
- optional legacy app smoke: `EC2 t3.small + gp3 40GiB`

## Environment Split

- 로컬 개발: Docker Compose 기반 PostgreSQL/Kafka
- 로컬 small-budget 근사 검증: `compose.t3micro.yml`
- 로컬 HTTP 부하테스트: `compose.loadtest.yml`
- cloud 1억 건 조회 테스트: OCI A1 PostgreSQL fixture + off-host/cloud k6 runner
- 비용형 DB/capacity 기준: [infra/terraform/oci/paid-a1-postgres](infra/terraform/oci/paid-a1-postgres/README.md)
- reverse proxy baseline: [ops/nginx](ops/nginx/README.md)
- monitoring baseline: [ops/prometheus](ops/prometheus/README.md)

## Delivery Flow

- 작업 브랜치는 `main`에서 짧게 분기하고 PR base는 `main`으로 둡니다.
- PR 리뷰와 backend/frontend CI 통과 후 `main`에 병합합니다.
- `main` merge SHA는 staging에 자동 배포합니다.
- production은 staging 성공 같은 SHA만 GitHub Environment 수동 승인 또는 `prod-*` tag로 승격합니다.
- 미완성 기능은 장기 `develop` 브랜치 대신 feature flag로 기본 비노출 처리합니다.

## 관련 문서

- 백엔드 상세 설명: [back/README.md](back/README.md)
- 프런트 실행 가이드: [front/README.md](front/README.md)
- OCI A1 PostgreSQL baseline: [infra/terraform/oci/paid-a1-postgres/README.md](infra/terraform/oci/paid-a1-postgres/README.md)
- Nginx reverse proxy baseline: [ops/nginx/README.md](ops/nginx/README.md)
- Prometheus/Grafana baseline: [ops/prometheus/README.md](ops/prometheus/README.md)

## 프로젝트에서 강조하고 싶은 점

이 프로젝트는 "웹뱅킹 화면을 만들었다"보다 "정합성 있는 금융 도메인을 작은 인프라에서 어디까지 운영 가능하게 만들 수 있는지 검증했다"에 가깝습니다.
대량 거래 조회, outbox/SSE 알림, admission control, 운영 evidence를 코드와 문서의 같은 레벨에서 관리하는 것을 프로젝트의 기본 규칙으로 삼았습니다.
