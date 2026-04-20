# Auth Forgot-Password Recovery Design

## Goal

로그인되지 않은 사용자가 recovery token으로 비밀번호를 재설정할 수 있는 public forgot-password 플로우를 추가한다. 메일/문자 채널이 아직 없으므로, token 값은 public 응답에 직접 노출하지 않고 internal exact lookup으로만 확인할 수 있게 한다.

## Scope

- 포함:
  - public password recovery request API
  - public password recovery confirm API
  - internal recovery token exact lookup API
  - recovery token 저장 테이블과 TTL/status 관리
  - 성공 시 password hash 교체 + active refresh session revoke
- 제외:
  - 메일/문자 발송 채널
  - 관리자 강제 비밀번호 초기화
  - access token 즉시 폐기
  - forgot-password UI
  - 비밀번호 정책 확장

## Current Constraints

- 현재 공개 비밀번호 재설정은 JWT user가 current password를 아는 self-service reset만 지원한다.
- `bank_user`는 `login_id`, `password_hash`, status와 login protection 메타데이터만 가진다.
- 메일/문자/이메일 컬럼이나 외부 연락 채널이 없다.
- refresh session revoke는 기존 `RefreshTokenSessionWritePort`로 이미 지원된다.
- `RequestIdFilter`의 `X-Request-Id`는 trace 전용으로 유지하고, password recovery handoff id는 별도로 발급해 internal exact lookup과 연결할 수 있다.

## Approaches Considered

### 1. Public request + public confirm + internal exact lookup

권장안이다. public request는 항상 `204`를 반환하고, active user가 있으면 recovery token을 발급한다. 토큰 값은 `requestId`를 키로 하는 internal lookup에서만 확인한다. 메일/문자 채널이 없는 현재 제약과 user enumeration 방지 요구를 같이 만족한다.

### 2. Public request 응답 본문에 recovery token 직접 반환

구현은 가장 빠르다. 하지만 `loginId`만 알면 token을 받아갈 수 있으므로 보안 경계가 크게 무너진다. 현재 저장소 규칙과 auth generic failure 원칙에 맞지 않는다.

### 3. Internal admin만 발급, public confirm만 제공

안전하지만 forgot-password self-service 플로우라기보다 운영자 보조 reset에 가깝다. 사용자 요청 경로와 후속 메일/문자 채널 재사용성이 떨어진다.

## Selected Design

권장안 1을 채택한다. public request와 confirm은 일반 사용자 경로로 제공하고, recovery token 값 자체는 internal exact lookup에서만 확인한다. public request는 trace용 `X-Request-Id`와 분리된 서버 발급 handoff requestId를 별도 response header로 반환한다. handoff requestId 생성 책임은 controller가 아니라 password recovery use case 안에 둔다. 이렇게 하면 현재 연락 채널이 없어도 로컬/운영 검증이 가능하고, 후속 메일/문자 채널을 붙일 때 public API 계약을 바꾸지 않아도 된다.

## API Contract

### `POST /api/v1/auth/password-recovery/request`

- 인증: 없음
- request body:
  - `loginId`
- response:
  - 항상 `204 No Content`
  - `X-Request-Id` 헤더는 기존 필터가 trace 용도로 채운다.
  - `X-Password-Recovery-Request-Id` 헤더에 서버가 발급한 handoff requestId를 반환한다.
- 동작:
  - caller가 보낸 `X-Request-Id` 값은 recovery token `request_id`로 저장하지 않는다.
  - password recovery use case가 새 handoff requestId를 생성해 `auth_password_recovery_token.request_id`에 저장한다.
  - active user가 존재하면 새 recovery token을 발급한다.
  - 같은 user의 기존 `PENDING` token은 `SUPERSEDED` 상태로 바꾼다.
  - unknown user, non-active user여도 응답은 동일하게 유지한다.

### `POST /api/v1/auth/password-recovery/confirm`

- 인증: 없음
- request body:
  - `recoveryToken`
  - `newPassword`
- response:
  - 성공: `204 No Content`
  - 실패: `401 password recovery failed`
- 동작:
  - token hash exact lookup
  - `PENDING` + `expires_at > now` + user `ACTIVE` 확인
  - 성공 시 password hash 교체, token `USED`, active refresh session revoke

### `GET /internal/api/v1/auth/password-recovery-tokens/by-request-id?requestId=...`

- 인증: internal service token + `AUTH_ADMIN`
- response:
  - `requestId`
  - `userId`
  - `loginId`
  - `recoveryToken`
  - `tokenStatus`
  - `expiresAt`
  - `usedAt`
  - `createdAt`
- 실패:
  - wrong scope/missing token: `401`
  - missing/blank requestId: `400`
  - unknown requestId: `404`

## Data Model

새 테이블 `auth_password_recovery_token`을 추가한다.

- 컬럼:
  - `id BIGSERIAL PRIMARY KEY`
  - `request_id VARCHAR(64) NOT NULL UNIQUE`
  - `user_id BIGINT NOT NULL`
  - `login_id VARCHAR(80) NOT NULL`
  - `token_hash VARCHAR(64) NOT NULL UNIQUE`
  - `token_ciphertext TEXT NOT NULL`
  - `token_nonce VARCHAR(64) NOT NULL`
  - `token_status VARCHAR(16) NOT NULL`
    - `PENDING`, `USED`, `EXPIRED`, `SUPERSEDED`
  - `expires_at TIMESTAMP WITH TIME ZONE NOT NULL`
  - `used_at TIMESTAMP WITH TIME ZONE NULL`
  - `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
  - `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`
- 인덱스:
  - `uq_auth_password_recovery_token_request_id`
  - `uq_auth_password_recovery_token_hash`
  - `idx_auth_password_recovery_token_status_expiry (token_status, expires_at ASC, user_id ASC)`

`request_id` exact lookup은 internal retrieval용, `token_hash` exact lookup은 confirm용이다. `status + expires_at` 인덱스는 후속 cleanup이나 만료 스캔을 bounded path로 유지한다.

## Token Generation And Storage

- token plain text:
  - 32 random bytes를 base64url-no-padding 문자열로 인코딩
- token hash:
  - `SHA-256` hex
- token encrypted storage:
  - `AES-GCM`
  - TOTP secret manager와 같은 암호화 패턴을 따르되 password recovery 전용 port로 분리
- TTL:
  - 기본 `15분`
  - property 예시: `security.password-recovery.ttl-seconds`

plain token은 public 응답에 실리지 않는다. DB에는 exact lookup용 hash와 internal 조회용 ciphertext/nonce만 저장한다.

## Request Flow

1. `loginId`로 user exact lookup
2. user가 없거나 `ACTIVE`가 아니면 아무 token도 만들지 않고 종료
3. active user면 새 token 생성
4. 같은 user의 `PENDING` token을 `SUPERSEDED`로 변경
5. 새 row insert

public 응답은 항상 동일하게 `204`다.

## Confirm Flow

1. `recoveryToken` hash 계산
2. token row `FOR UPDATE` exact lookup
3. row가 없거나 status가 `PENDING`이 아니면 generic `401`
4. `expires_at <= now`면 `EXPIRED`로 갱신 후 generic `401`
5. user `ACTIVE` 확인, 아니면 generic `401`
6. password hash 교체
7. token `USED`, `used_at = now`
8. active refresh session 전체 revoke

이 순서는 password reset 성공/실패와 token 상태 전이가 같은 transaction 안에서 끝나도록 유지한다.

## Error Policy

- request:
  - validation 오류만 `400`
  - 나머지는 user 존재 여부와 관계없이 `204`
- confirm:
  - validation 오류 `400`
  - wrong/expired/used token, inactive user는 모두 `401 password recovery failed`
- internal lookup:
  - missing/wrong scope `401`
  - missing/blank requestId `400`
  - unknown requestId `404`

## Security Notes

- public request 응답으로 token을 직접 노출하지 않는다.
- generic failure를 유지해 loginId 존재 여부와 token 상태를 외부에서 추정하지 못하게 한다.
- access token 즉시 폐기는 하지 않고, 기존 self-service reset과 동일하게 refresh revoke까지만 보장한다.
- internal lookup은 운영/로컬 검증용 임시 handoff 채널이다. 조회 키는 trace request-id가 아니라 password recovery handoff requestId다. 메일/문자 채널이 붙으면 plain token 전달 책임은 그 채널로 이동한다.

## Testing Strategy

- unit:
  - request service: active/non-active/unknown user 처리
  - confirm service: success, wrong token, expired token, used token, inactive user
  - token secret manager: generate/hash/encrypt/decrypt round-trip
- integration:
  - request existing/non-existing `loginId` 모두 `204`
  - internal lookup returns token only for existing requestId with `AUTH_ADMIN`
  - confirm success 뒤 old password login 실패, new password login 성공
  - confirm success 뒤 기존 refresh token refresh 실패
  - used/expired token 재사용 시 generic `401`

## Rollout And Follow-Ups

- 이번 PR은 forgot-password core flow만 닫는다.
- 후속 작업:
  - 메일/문자 발송 채널 연동
  - request rate limit / abuse protection
  - expired token cleanup job
  - 운영 감사 로그/메트릭 추가
