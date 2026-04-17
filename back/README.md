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

## Transfer Reversal

기존 BOOKED 송금은 직접 수정하지 않고 reversal transaction을 추가해 취소/정정합니다.

- 공개 endpoint:
  - `POST /api/v1/transfers`
  - `POST /api/v1/transfers/{transactionReference}/reversal`
- reversal 요청 필드:
  - `sourceAccountId`
  - `reversalReason`: `CANCEL` | `CORRECTION`
  - `summary`
- reversal 응답 필드:
  - `originalTransactionReference`
  - `reversalTransactionReference`
  - `sourceAccountId`
  - `targetAccountId`
  - `amountMinor`
  - `currencyCode`
  - `availableBalanceAfterMinor`
  - `bookedAt`
  - `status`
- write 기준:
  - 원본 transfer와 reversal 관계는 `transfer_reversal` 테이블로 관리
  - 원본 ledger row는 수정하지 않고 반대 방향 ledger entry 2건을 새 transaction reference로 추가
  - 원본 `transaction_read_model` 상태는 `REVERSED` 로 전이하고 reversal read model row 2건을 추가
  - outbox는 `TransferReversed` event를 별도 적재
- 충돌 기준:
  - 같은 원본 transfer를 다시 reversal 하면 `409 transfer is already reversed`
  - reversal 시 target 계좌 잔액이 부족하면 `409 reversal target balance is not enough`
- 운영 주의:
  - 이번 범위는 full reversal만 지원하고 partial reversal은 제외
  - notification inbox consumer는 아직 `TransferReversed` fan-out을 처리하지 않음

## Public Login Protection

공개 login 경로 `/api/v1/auth/login`에는 brute-force 1차 방어 기준이 기본 적용됩니다.

- 기본값:
  - `SECURITY_LOGIN_PROTECTION_MAX_FAILURES=5`
  - `SECURITY_LOGIN_PROTECTION_LOCK_SECONDS=900`
  - `SECURITY_LOGIN_PROTECTION_RESET_WINDOW_SECONDS=900`
- 동작 기준:
  - 같은 `loginId`에서 연속 `5회` 실패하면 `15분` 임시 잠금
  - 마지막 실패 후 `15분`이 지나면 실패 카운트는 다시 `1`부터 계산
  - 성공 login 시 `failed_login_count`, `last_login_failed_at`, `login_locked_until`은 reset
  - 외부 응답은 존재 여부/잠금 여부를 드러내지 않도록 항상 `401 login failed` 유지
- 상태 우선순위:
  - 수동 운영 상태 `user_status=LOCKED|DISABLED`가 임시 잠금보다 우선
  - 임시 brute-force 잠금은 `login_locked_until`로만 관리하고 `user_status`는 직접 바꾸지 않음

### login 실패 감사 로그

login 실패/잠금은 structured log 한 줄로 남습니다.

| field | 의미 | 운영 사용 기준 |
| --- | --- | --- |
| `requestId` | 호출 상관키 | login incident drill-down |
| `loginIdHash` | raw `loginId` 대신 남기는 SHA-256 hash | 개인정보 노출 없이 동일 loginId 반복 추적 |
| `userId` | 존재하는 사용자면 user id, 아니면 `-` | 계정 존재/대상 추적 |
| `failureCount` | 현재 누적 실패 횟수 | 임계치 도달 여부 판단 |
| `remainingAttempts` | 임계치까지 남은 횟수 | 운영 위험도 판단 |
| `lockedUntil` | 임시 잠금 만료 시각 | 잠금 해제 예상 시각 |
| `reason` | `INVALID_CREDENTIALS`, `LOCKED_THRESHOLD_REACHED`, `ACCOUNT_TEMPORARILY_LOCKED`, `USER_STATUS_LOCKED`, `USER_DISABLED` | 검색/집계 기준 |
| `path` | 현재 요청 path | 공개 login 경로 확인 |

### login reset 로그

성공 login 으로 failure state를 지웠고 이전 실패 흔적이 있었던 경우에만 `info` 로그를 남깁니다.

| field | 의미 |
| --- | --- |
| `requestId` | 성공 요청 상관키 |
| `loginIdHash` | 같은 loginId 추적용 SHA-256 hash |
| `userId` | 성공 사용자 id |
| `previousFailureCount` | reset 전 실패 누적 횟수 |
| `previousLockedUntil` | reset 전 잠금 만료 시각 |
| `path` | 현재 요청 path |

## Refresh Token Session

공개 인증 경로는 access token JWT와 함께 refresh token rotation을 기본 지원합니다.

- 공개 endpoint:
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/refresh`
- 응답 필드:
  - `accessToken`
  - `refreshToken`
  - `tokenType`
  - `expiresAt`
  - `refreshExpiresAt`
  - `userId`
- 기본값:
  - `SECURITY_JWT_ACCESS_TOKEN_TTL_SECONDS=900`
  - `SECURITY_JWT_REFRESH_TOKEN_TTL_SECONDS=1209600`
- 저장 기준:
  - raw refresh token은 응답으로만 한 번 내려가고 DB에는 `SHA-256 token_hash`만 저장
  - 저장 테이블은 `auth_refresh_token_session`
  - 성공 refresh 시 기존 row는 `ROTATED`, 새 row는 `ACTIVE`
- 거절 기준:
  - 만료, 이미 rotation 된 token, 존재하지 않는 token은 모두 `401 refresh failed`
  - `user_status=LOCKED|DISABLED` 사용자는 refresh로 새 token pair를 발급받지 못함
- 운영 주의:
  - 최소 범위에서는 logout / logout-all / 세션 목록 조회를 제공하지 않음
  - raw refresh token, plaintext secret은 로그/DB에 남기지 않음

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
- `404`, `409`, `500`은 최소 alert 기본값에서는 제외하고, 아래 후속 운영 기준으로 별도 판단합니다.

### 404/409/500 후속 수집 패턴

#### 404 운영 기준

- 대표 원인:
  - `user is not found`
  - `membership is not found`
- 수집 패턴:

```text
404 후보:
  "internal auth status update failed" AND "httpStatus=404"

같은 actor의 404 반복:
  "internal auth status update failed" AND "httpStatus=404" AND "actorSubject=<actor-subject>" AND "path=<endpoint>"
```

- 운영 의미:
  - stale `userId`/`accountId` 재사용
  - 수동 호출 대상 오입력
  - 배치가 이미 정리된 대상을 늦게 호출한 경우

#### 409 운영 기준

- 현재 known 상태:
  - 내부 auth status update 경로에서 `409` 대표 error 문구는 아직 고정돼 있지 않습니다.
  - 현재 runbook에서는 `httpStatus=409`와 같은 `actorSubject`/`path` 반복 패턴을 먼저 수집하고, 세부 error 분류는 후속 구현 이슈로 남깁니다.
- 수집 패턴:

```text
409 후보:
  "internal auth status update failed" AND "httpStatus=409"

같은 actor의 409 반복:
  "internal auth status update failed" AND "httpStatus=409" AND "actorSubject=<actor-subject>" AND "path=<endpoint>"
```

- 운영 의미:
  - 같은 대상에 대한 중복 상태 변경 시도
  - caller 재시도 정책 또는 수동 재실행 충돌
  - 상태 전이 전후 확인이 필요한 경쟁 조건 후보

#### 500 운영 기준

- 대표 원인:
  - `requestId is not initialized` 같은 서버 상태 불일치
  - persistence/runtime 예외로 인한 internal error
- 수집 패턴:

```text
500 후보:
  "internal auth status update failed" AND "httpStatus=500"

requestId 우선 drill-down:
  "internal auth status update failed" AND "httpStatus=500" AND "requestId=<request-id>"
```

- 운영 의미:
  - 운영 입력 오류보다 서버 측 장애 후보 우선
  - 같은 시간대 `actorSubject`/`path` 확산 여부 확인 필요
  - success audit exact lookup 부재만으로 종료하지 말고 로그 타임라인 재확인이 필요

### 404/409/500 제외/승격 조건

#### 404 운영 기준

- 제외 조건:
  - 단발 `404`이고 같은 actor가 직후 대상 식별자를 수정해 성공 호출로 전환한 경우
  - 수동 점검 과정에서 잘못된 `userId`/`accountId`를 한 번 입력한 경우
- 승격 조건:
  - 같은 `actorSubject + path`에서 동일 target miss가 `10분 내 3회 이상` 반복되면 warning 후보
  - 같은 배치 또는 actor가 여러 target에서 `404`를 확산시키면 critical 후보

#### 409 운영 기준

- 제외 조건:
  - 같은 actor의 단발 중복 호출이고, 인접 시간대에 성공 감사 row가 확인되는 경우
  - 수동 재실행이나 caller retry가 이미 적용된 상태로 보이는 경우
- 승격 조건:
  - 같은 `actorSubject + path + requestedStatus` 조합의 `409`가 `10분 내 3회 이상` 반복되고 success audit row가 확인되지 않으면 warning 후보
  - 서로 다른 actor가 같은 target에 충돌하는 정황이 보이면 critical 후보

#### 500 운영 기준

- 제외 조건:
  - 기본적으로 제외하지 않습니다.
- 승격 조건:
  - `500` 한 건만으로도 incident 후보로 triage 합니다.
  - 같은 시간대에 `500`이 2회 이상 반복되거나 여러 actor/path로 확산되면 critical 후보로 봅니다.

### 404/409/500 requestId 추적 차이

- `404`:
  - `requestId`로 실패 로그 한 줄을 찾은 뒤, 같은 `targetUserId`/`targetAccountId`가 실제로 존재했는지 먼저 확인합니다.
  - success audit row가 없더라도 바로 장애로 보지 않고, stale target 또는 수동 오입력 가능성을 먼저 분리합니다.
- `409`:
  - `requestId`로 실패 시점을 찾은 뒤, 같은 `actorSubject + path + requestedStatus` 조합의 직전/직후 호출을 같이 봅니다.
  - 다른 `requestId`의 success audit row가 근접 시간대에 있으면 중복 호출 또는 재시도 충돌 가능성을 우선 봅니다.
- `500`:
  - `requestId`를 가장 먼저 확보하고, 같은 시간대 `actorSubject + path + error` 확산 여부를 바로 확인합니다.
  - success audit row가 없고 같은 오류가 반복되면 caller 오입력보다 서버 장애 후보로 바로 승격합니다.

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

## Outbox Ops Runbook

Kafka producer 를 붙인 이후 outbox backlog 는 actuator health 와 내부 ops 경로를 같이 봐야 복구 판단이 빨라집니다.

- 내부 ops endpoint:
  - `GET /internal/api/v1/outbox/summary`
  - `GET /internal/api/v1/outbox/failed-events?limit=<n>`
  - `POST /internal/api/v1/outbox/recovery/stale-sending`
- health endpoint:
  - `GET /actuator/health`

### 준비할 env

```bash
OUTBOX_OPS_ENABLED=true
OUTBOX_OPS_TOKEN_HEADER=X-Outbox-Ops-Token
OUTBOX_OPS_TOKEN=dev-outbox-ops-token
OUTBOX_OPS_FAILED_LIST_LIMIT=20
OUTBOX_OPS_HEALTH_MAX_LAG_SECONDS=120
OUTBOX_OPS_HEALTH_MAX_FAILED_COUNT=10
OUTBOX_OPS_HEALTH_MAX_STALE_SENDING_COUNT=0
```

### 예시 스크립트

failed backlog 조회:

```bash
tools/ops/outbox-find-failed-events.sh \
  http://localhost:8080 \
  "$OUTBOX_OPS_TOKEN" \
  20
```

stale `SENDING` 수동 회수:

```bash
tools/ops/outbox-recover-stale-sending.sh \
  http://localhost:8080 \
  "$OUTBOX_OPS_TOKEN"
```

### 직접 호출 예시

summary 조회:

```bash
curl --fail-with-body --silent --show-error \
  --get \
  --header "X-Outbox-Ops-Token: ${OUTBOX_OPS_TOKEN}" \
  "http://localhost:8080/internal/api/v1/outbox/summary"
```

health 확인:

```bash
curl --fail-with-body --silent --show-error \
  --get \
  "http://localhost:8080/actuator/health"
```

### health 상태 해석

- `UP`: `lagSeconds`, `failedCount`, `staleSendingCount` 가 모두 설정 임계값 이하다.
- `OUT_OF_SERVICE`: outbox backlog 가 임계값을 넘었고 `/actuator/health` 는 `503`으로 내려간다.
- `OUT_OF_SERVICE` 여도 앱 전체 장애와 동일시하지 말고 먼저 `/internal/api/v1/outbox/summary` 로 lag/failure/stale 축 중 어떤 값이 넘었는지 확인한다.

### 기본 triage 순서

1. `/actuator/health` 가 `503`이면 `/internal/api/v1/outbox/summary` 를 조회해 `lagSeconds`, `failedCount`, `staleSendingCount` 중 초과 축을 확인합니다.
2. `failedCount` 가 크면 `tools/ops/outbox-find-failed-events.sh` 로 bounded failed list 를 보고 `eventKey`, `retryCount`, `lastError` 를 먼저 확인합니다.
3. `staleSendingCount` 가 0보다 크면 `tools/ops/outbox-recover-stale-sending.sh` 를 한 번만 호출하고, 응답의 `recoveredCount` 와 이후 summary 변화를 확인합니다.
4. recovery 이후에도 `lagSeconds` 또는 `failedCount` 가 계속 증가하면 broker 연결, Kafka topic 설정, consumer 적재 지연을 별도 incident 로 분리합니다.

### 운영 주의사항

- stale recovery 는 직접 publish 가 아니라 stale `SENDING` row 를 `PENDING` 으로 되돌리는 동작입니다.
- recovery 대상은 `outbox.poller.stale-after-seconds` 를 넘긴 row 만 포함합니다.
- failed list 는 `availableAt ASC, id ASC` 순서의 bounded query 이므로, 대량 backlog 에서도 즉시 재시도 대상부터 확인할 수 있습니다.
- `lastError` 는 outbox table 의 짧은 힌트만 남기므로, 상세 stack trace 는 앱 로그와 Kafka client 로그를 같이 봐야 합니다.

### rollback 기준

- 운영에서 내부 ops 경로를 임시 차단해야 하면 `OUTBOX_OPS_ENABLED=false` 로 내려 endpoint 노출만 끕니다.
- 수동 recovery 가 과도하게 반복되면 더 이상 반복 호출하지 말고 poller 자동 reclaim 과 broker 장애 복구를 먼저 확인합니다.
- 이 runbook 경로는 outbox schema 나 payload 를 수정하지 않으므로, rollback 은 endpoint 비활성화와 PR revert 로 제한합니다.

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
