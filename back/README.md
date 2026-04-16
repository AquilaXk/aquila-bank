# Aquila Bank Backend

Spring Boot 4 기반 백엔드 애플리케이션입니다.

## Goal

- 실시간 알림과 1억 건 규모의 거래 조회를 `t3.micro` 환경에서도 원활하게 처리하는 구조를 목표로 합니다.
- 로컬/배포 환경 모두 `PostgreSQL 18`을 표준 DB 버전으로 사용합니다.

## Package Structure

```text
com.aquilabank
├── domain
├── global
└── standard
    └── util
```

- `domain`: 도메인 엔티티, 유스케이스, 포트, 도메인 서비스
- `global`: 설정, 보안, 웹 어댑터, DB 어댑터, 예외 처리
- `standard/util`: 공통 규약과 최소 유틸리티

## Architecture

- 헥사고날 아키텍처 준수를 명시적 원칙으로 삼습니다.
- domain은 Spring MVC, JPA, 외부 API SDK에 직접 의존하지 않습니다.
- inbound/outbound adapter, config, filter, interceptor, repository 구현체는 `global`에 둡니다.
- 공통 helper는 `standard/util`에 두되, 도메인 규칙이나 유스케이스 판단은 넣지 않습니다.

## Stack

- Java 21
- Gradle Kotlin DSL
- Spring Boot 4
- PostgreSQL 18
- Flyway
- Spring JDBC / HikariCP
- Spring Boot Actuator

## Run

```bash
./gradlew bootRun
```

## Local Database

```bash
cp ../.env.example ../.env
cd ..
docker compose up -d postgres
```

로컬 DB는 루트 [compose.yml](/Users/aquila/Custom/GitProjects/aquila-bank/compose.yml)의 `postgres:18` 컨테이너를 기준으로 사용합니다.

백엔드는 [back/.env.example](/Users/aquila/Custom/GitProjects/aquila-bank/back/.env.example)를 복사한 뒤 실행합니다.

```bash
cp back/.env.example back/.env
set -a
source back/.env
set +a
./back/gradlew -p back bootRun
```

- 애플리케이션 시작 시 `back/src/main/resources/db/migration`의 Flyway 마이그레이션이 자동 적용됩니다.
- 기본값은 `t3.micro`를 전제로 작은 커넥션 풀과 짧은 DB 타임아웃을 사용합니다.
- 운영 기본 인증 방식은 bearer JWT 입니다.
- `dev`/`test` 프로필에서는 필요 시 `X-Account-Id` 헤더 fallback을 사용할 수 있습니다.

## Internal Auth Admin Runbook

내부 auth status update는 공개 로그인 경로와 분리된 내부 운영 surface 입니다.

- 공통 endpoint:
  - `PUT /internal/api/v1/auth/users/{userId}/status`
  - `PUT /internal/api/v1/auth/users/{userId}/memberships/{accountId}/status`
- 공통 필수 헤더:
  - `X-Auth-Bootstrap-Token`: `SECURITY_AUTH_BOOTSTRAP_API_TOKEN` 값
  - `X-Subject`: 호출 주체 식별값. 예) `ops-admin`, `fraud-batch`
  - `X-Request-Id`: 운영 상관키. 누락 시 서버가 UUID를 생성하지만, 감사 추적 일관성을 위해 운영 호출에서는 직접 넣는 것을 기본값으로 사용합니다.
- 공통 필수 body 필드:
  - `reason`: 회수/비활성화 사유

### 준비할 env

`back/.env.example` 기준으로 아래 값이 준비되어 있어야 합니다.

```bash
SECURITY_BOOTSTRAP_SUBJECT_HEADER=X-Subject
SECURITY_AUTH_BOOTSTRAP_API_ENABLED=true
SECURITY_AUTH_BOOTSTRAP_API_TOKEN_HEADER=X-Auth-Bootstrap-Token
SECURITY_AUTH_BOOTSTRAP_API_TOKEN=dev-auth-bootstrap-api-token
```

로컬 실행 예시:

```bash
set -a
source back/.env
set +a
./back/gradlew -p back bootRun
```

### 예시 스크립트

운영 호출 예시는 아래 스크립트를 그대로 사용할 수 있습니다.

```bash
tools/ops/internal-auth-update-user-status.sh \
  http://localhost:8080 \
  "$SECURITY_AUTH_BOOTSTRAP_API_TOKEN" \
  ops-admin \
  auth-user-disable-20260416-001 \
  21 \
  DISABLED \
  fraud-review
```

```bash
tools/ops/internal-auth-update-membership-status.sh \
  http://localhost:8080 \
  "$SECURITY_AUTH_BOOTSTRAP_API_TOKEN" \
  ops-admin \
  auth-membership-revoke-20260416-001 \
  21 \
  1001 \
  REVOKED \
  manual-revoke
```

### 직접 호출 예시

user status update:

```bash
curl --fail-with-body --silent --show-error \
  --request PUT \
  --header "Content-Type: application/json" \
  --header "X-Auth-Bootstrap-Token: ${SECURITY_AUTH_BOOTSTRAP_API_TOKEN}" \
  --header "X-Subject: ops-admin" \
  --header "X-Request-Id: auth-user-disable-20260416-001" \
  --data '{"userStatus":"DISABLED","reason":"fraud-review"}' \
  "http://localhost:8080/internal/api/v1/auth/users/21/status"
```

membership status update:

```bash
curl --fail-with-body --silent --show-error \
  --request PUT \
  --header "Content-Type: application/json" \
  --header "X-Auth-Bootstrap-Token: ${SECURITY_AUTH_BOOTSTRAP_API_TOKEN}" \
  --header "X-Subject: ops-admin" \
  --header "X-Request-Id: auth-membership-revoke-20260416-001" \
  --data '{"membershipStatus":"REVOKED","reason":"manual-revoke"}' \
  "http://localhost:8080/internal/api/v1/auth/users/21/memberships/1001/status"
```

### 실패 조건

- `reason` 누락 또는 blank: `400 Bad Request`
- `X-Subject` 누락 또는 blank: `400 Bad Request`
- `X-Auth-Bootstrap-Token` 누락 또는 값 불일치: `401 Unauthorized`
- `X-Request-Id` 누락: 서버가 자동 생성하므로 요청 자체는 실패하지 않음. 다만 운영 감사 추적 키를 맞추기 위해 직접 지정하는 것을 기본값으로 사용

### 운영 주의사항

- `X-Subject`는 shared token 사용자 구분 대신 운영 주체를 남기는 값이므로 배치명/운영자 식별값을 짧고 고정된 slug로 사용합니다.
- `reason`은 감사 로그와 감사 테이블에 그대로 남으므로 길고 자유로운 문장보다 짧은 운영 사유 slug를 우선 사용합니다.
- `dev`/`test`의 `X-Account-Id` bootstrap fallback은 계좌 요청 테스트용이며, 내부 auth admin status update 인증 방식과 혼용하지 않습니다.

## Test

```bash
./gradlew test
```

## Java Style

- Google Java Style 기반 포맷 검사는 Spotless로 강제합니다.
- 검사: `./gradlew check`
- 자동 정리: `./gradlew spotlessApply`

## Local Concurrency

- 같은 워크트리에서 여러 스레드가 동시에 `test`/`check`를 돌리면 `back/build/test-results`를 공유해 충돌할 수 있습니다.
- 로컬 검증은 `tools/test/with-resource-lock.sh back-gradle-check ...`로 직렬화하는 것을 기본값으로 사용합니다.
