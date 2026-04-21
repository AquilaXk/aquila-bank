# Notification Preferences Design

**Issue:** `#162`
**Branch:** `feat/notification-preferences`

## Goal

알림 preference 설정 API를 추가해 사용자가 알림 카테고리와 채널별 수신 여부를 직접 조회/수정할 수 있게 한다.
이번 범위는 실무 기본 패턴인 `user 단위 기본 설정`만 지원하고, account override 없이도 안정적으로 확장 가능한 계약을 먼저 연다.

## Non-Goals

- account별 override 우선순위 규칙 추가
- notification inbox/search/read API 계약 변경
- 실제 발송 채널 consumer, outbox, fanout 경로 변경
- 프론트 화면 구현
- 관리자용 일괄 정책 편집 기능

## Current State

- notification 도메인은 inbox 조회, unread count, read/archive/delete까지 제공한다.
- 사용자별 notification preference 저장 스키마와 API는 아직 없다.
- 현재 principal 계약은 JWT user와 account principal을 모두 지원하지만, preference는 사용자 고유 선택이므로 `AuthenticatedUserPrincipal`만 허용하는 편이 안전하다.

## Decision Summary

1. preference는 `user` 단위 기본 설정만 지원한다.
2. 저장 단위는 `category + channel + enabled` row 모델로 둔다.
3. 미저장 조합은 서버 기본값으로 해석한다.
4. API는 `GET /api/v1/notifications/preferences`, `PUT /api/v1/notifications/preferences` 두 개만 연다.
5. account principal 호출은 `400`으로 막고, JWT user만 허용한다.
6. inbox read model과 별도 테이블로 분리해 읽기 비용과 변경 리스크를 낮춘다.

## Preference Model

### Category

- `TRANSACTIONAL`
- `SECURITY`
- `MARKETING`

### Channel

- `IN_APP`
- `EMAIL`
- `SMS`

### Default Policy

- `TRANSACTIONAL`
  - `IN_APP=true`
  - `EMAIL=true`
  - `SMS=false`
- `SECURITY`
  - `IN_APP=true`
  - `EMAIL=true`
  - `SMS=true`
- `MARKETING`
  - `IN_APP=false`
  - `EMAIL=false`
  - `SMS=false`

### Why Row Model

- category/channel 조합 추가가 단순하다.
- JSON blob보다 검증과 부분 변경이 명확하다.
- `(user_id, category, channel)` unique key로 정확한 upsert가 가능하다.
- 추후 account override가 필요해져도 동일 패턴으로 확장하기 쉽다.

## API Contract

### GET `/api/v1/notifications/preferences`

- JWT user 전용
- response는 모든 category/channel 조합을 항상 반환한다.
- DB row가 없는 조합도 서버 기본값으로 채워 응답한다.

```json
{
  "items": [
    {
      "category": "TRANSACTIONAL",
      "channel": "IN_APP",
      "enabled": true
    }
  ]
}
```

### PUT `/api/v1/notifications/preferences`

- JWT user 전용
- request는 전체 조합 덮어쓰기 대신 변경 대상 목록만 받는다.
- 동일 조합 중복 요청은 `400`으로 막는다.
- 최소 1개 이상, 최대 32개 이하 항목만 허용한다.

```json
{
  "items": [
    {
      "category": "MARKETING",
      "channel": "EMAIL",
      "enabled": false
    }
  ]
}
```

### Error Policy

- `400`
  - account principal 호출
  - category/channel enum 값 오류
  - 빈 목록
  - 중복 조합 요청
- `401`
  - 인증 없음
- `200`
  - 조회 성공
- `204`
  - 저장 성공

## Domain Strategy

- `NotificationPreference`
  - 단일 category/channel 설정 row
- `NotificationPreferenceCategory`
- `NotificationPreferenceChannel`
- `NotificationPreferenceUpdateCommand`
- `NotificationPreferenceReadUseCase`
- `NotificationPreferenceUpdateUseCase`
- `NotificationPreferenceReadPort`
- `NotificationPreferenceWritePort`

도메인은 단순 검증과 기본값 merge만 담당하고, JDBC 세부 구현은 global persistence로 밀어낸다.

## Persistence Strategy

### Schema

새 테이블 `notification_preference`

- `id bigint generated always as identity`
- `user_id bigint not null`
- `category varchar(32) not null`
- `channel varchar(16) not null`
- `enabled boolean not null`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

제약과 인덱스:

- `unique (user_id, category, channel)`
- `index (user_id, category, channel)`

### Read

- `user_id` exact lookup으로 row 목록을 읽는다.
- 읽은 row를 기본 조합 맵에 merge 해서 누락 조합도 응답에 포함한다.
- 데이터 양이 작아도 메모리 후처리는 사용자 1명 범위의 고정 소형 집합에서만 수행한다.

### Write

- `INSERT ... ON CONFLICT (user_id, category, channel) DO UPDATE`
- 요청 항목만 upsert 한다.
- 전체 삭제/재삽입은 피한다.

## Controller Strategy

- 기존 `NotificationController`에 preference endpoint를 추가한다.
- user principal이 아니면 즉시 `IllegalArgumentException("notification preferences require user principal")`
- 별도 controller로 쪼개지 않고 notification API 집합을 한 곳에서 유지한다.

## Testing Strategy

- controller test
  - JWT user 조회/수정 성공
  - account principal 차단
  - 중복 조합 요청 400
- domain unit test
  - 기본값 merge
  - 중복 조합 검증
- JDBC integration test
  - 기본값 fallback
  - upsert overwrite
- API integration test
  - JWT user가 조회 후 저장하고 다시 조회했을 때 값이 반영된다.
  - 다른 user 설정은 분리된다.

## Risks

- account principal까지 허용하면 사용자 개인 설정과 계좌 공용 설정 의미가 섞인다.
- JSON blob 저장은 지금은 쉬워 보여도 enum 검증과 부분 변경 추적이 약하다.
- 기본값 정책을 코드와 문서에 동시에 고정하지 않으면 운영 중 drift가 생길 수 있다.

## Follow-Up

- account override
- 관리자 정책 override
- 발송 경로가 preference를 실제로 반영하는 consumer 검증
