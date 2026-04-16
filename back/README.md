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
  - `reasonCode`: 분류용 고정 코드. 예) `FRAUD_REVIEW`, `OPS_MANUAL`
  - `reasonDetail`: 운영 문맥 상세값
- legacy compatibility:
  - `reason` 단일 필드는 한시 호환 경로로만 허용되고, 서버 내부에서는 `reasonCode=LEGACY_FREE_TEXT`로 정규화됩니다.

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
  FRAUD_REVIEW \
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
  OPS_MANUAL \
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
  --data '{"userStatus":"DISABLED","reasonCode":"FRAUD_REVIEW","reasonDetail":"fraud-review"}' \
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
  --data '{"membershipStatus":"REVOKED","reasonCode":"OPS_MANUAL","reasonDetail":"manual-revoke"}' \
  "http://localhost:8080/internal/api/v1/auth/users/21/memberships/1001/status"
```

### 실패 조건

- `reasonCode` 누락: `400 Bad Request`
- `reasonDetail` 누락 또는 blank: `400 Bad Request`
- `X-Subject` 누락 또는 blank: `400 Bad Request`
- `X-Auth-Bootstrap-Token` 누락 또는 값 불일치: `401 Unauthorized`
- `reason`과 `reasonCode`/`reasonDetail` 동시 사용: `400 Bad Request`
- `X-Request-Id` 누락: 서버가 자동 생성하므로 요청 자체는 실패하지 않음. 다만 운영 감사 추적 키를 맞추기 위해 직접 지정하는 것을 기본값으로 사용

### 운영 주의사항

- `X-Subject`는 shared token 사용자 구분 대신 운영 주체를 남기는 값이므로 배치명/운영자 식별값을 짧고 고정된 slug로 사용합니다.
- `reasonCode`는 alert/filter 기준으로 쓰고, `reasonDetail`은 감사 로그와 감사 테이블에 남는 운영 문맥으로 사용합니다.
- `reasonDetail`은 길고 자유로운 문장보다 짧은 운영 사유 slug를 우선 사용합니다.
- legacy `reason`은 한시 호환 경로라서 새 운영 호출과 스크립트에서는 사용하지 않는 것을 기본값으로 둡니다.
- `dev`/`test`의 `X-Account-Id` bootstrap fallback은 계좌 요청 테스트용이며, 내부 auth admin status update 인증 방식과 혼용하지 않습니다.

### 실패 감사 로그 필수 필드

실패 감사 로그는 `warn` 레벨 한 줄로 남고, prefix는 항상 `internal auth status update failed`입니다.

| field | 의미 | 운영 사용 기준 |
| --- | --- | --- |
| `requestId` | 호출 상관키 | 장애 drill-down 전용, alert group key로 사용 금지 |
| `httpStatus` | API 응답 status | `401`/`400` alert 분류 1순위 |
| `actorSubject` | 호출 주체 식별값 | 반복 오호출 actor 추적 |
| `targetUserId` | 대상 user 식별자 | 대상 범위 확인 |
| `targetAccountId` | 대상 account 식별자 | membership 경로만 값 존재, user status는 `-` |
| `requestedStatus` | 요청한 상태값 | `DISABLED`, `REVOKED` 같은 실패 의도 확인 |
| `reasonCode` | 분류 코드 | alert/filter 기준 |
| `reasonDetail` | 운영 사유 상세 | 오호출/배치 drift 확인 |
| `path` | 실패 endpoint | user/membership 경로 구분 |
| `error` | 서버가 반환한 실패 원인 | validation/token 오류 분류 |

### 수집 패턴

로그 플랫폼 문법은 각자 다르므로 아래 키 조합만 그대로 매핑합니다.

```text
전체 실패:
  "internal auth status update failed"

401 집계:
  "internal auth status update failed" AND "httpStatus=401"

같은 actorSubject의 400 추적:
  "internal auth status update failed" AND "httpStatus=400" AND "actorSubject=<actor-subject>"

requestId drill-down:
  "internal auth status update failed" AND "requestId=<request-id>"
```

- alert 집계 차원은 `httpStatus`, `actorSubject`, `path`, `error`를 우선 사용합니다.
- `requestId`는 고카디널리티라 alert 집계 기준으로 쓰지 않고 incident drill-down에만 사용합니다.
- `reasonCode` 차원은 `FRAUD_REVIEW`, `OPS_MANUAL`, `LEGACY_FREE_TEXT` 같은 고정값만 사용합니다.
- 구버전 로그가 섞여 `httpStatus`가 없는 기간은 한시적으로 `error=bootstrap token is invalid`를 `401`, `error=actorSubject header is required|reasonCode is required|reasonDetail is required|reason and reasonCode/reasonDetail cannot be used together`를 `400` fallback으로 사용합니다.

### 최소 alert 기준

- `401 Unauthorized`: `5분 내 3회 이상`이면 warning, 같은 window에서 `10회 이상`이면 critical 후보로 봅니다.
- `400 Bad Request`: 같은 `actorSubject`에서 연속 `2회 이상`이면 warning, `5회 이상`이면 critical 후보로 봅니다.
- `actorSubject=-`인 `400`은 헤더 누락 성격이므로 운영 스크립트 drift 또는 수동 호출 오류로 분류합니다.
- `404`, `409`, `500`은 이번 최소 범위에서 제외하고 후속 이슈로 분리합니다.

### requestId 장애 추적 절차

1. alert 또는 문의에서 `requestId`를 확보합니다. alert payload에 `requestId`가 없으면 같은 시간대 `actorSubject + path + error`로 실패 로그를 먼저 좁힙니다.
2. 앱 로그에서 같은 `requestId`를 재검색해 최초 실패 시점, 재시도 여부, 같은 actor 반복 여부를 확인합니다.
3. 성공 전환 여부가 필요하면 success audit exact lookup으로 같은 `requestId`를 조회합니다.
4. success audit row가 없으면 실패-only incident로 보고 토큰, `X-Subject`, request body drift를 runbook 체크리스트로 확인합니다.

success audit exact lookup 예시:

```bash
tools/ops/internal-auth-find-status-change-audit.sh \
  http://localhost:8080 \
  "$SECURITY_AUTH_BOOTSTRAP_API_TOKEN" \
  auth-user-disable-20260416-001
```

- exact lookup은 성공 변경 row만 반환합니다.
- wrapper script 내부에서 exact lookup endpoint, `X-Auth-Bootstrap-Token`, `requestId` query를 고정합니다.
- failure 원본은 structured log이므로 incident 시작점은 항상 로그 검색입니다.

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
