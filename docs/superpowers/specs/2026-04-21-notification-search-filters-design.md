# Notification Search Filters Design

**Issue:** `#160`
**Branch:** `feat/notification-search-filters`

## Goal

기존 `GET /api/v1/notifications` 최소 cursor 목록 API를 유지하면서, 검색/필터 목적의 읽기 경로를 별도로 추가한다.
이번 범위는 실무에서 가장 흔하게 쓰는 `readStatus`, `eventType`, `from/to` 필터만 지원하고, `created_at DESC, id DESC` keyset 정렬과 `t3.micro` 운영 제약을 함께 지킨다.

## Non-Goals

- 기존 `GET /api/v1/notifications` 계약 변경
- unread count, SSE, bulk read/archive/delete 계약 변경
- 제목/본문 free-text 검색
- Elasticsearch, Redis, 별도 검색 엔진 도입
- notification write/consumer/outbox 경로 변경

## Current State

- 현재 목록 API는 `limit + cursor`만 받는 최소 keyset page 계약이다.
- `NotificationListQuery`는 `limit`, `cursor`만 들고 있다.
- `JdbcNotificationInboxRepository`는 account principal과 JWT user principal을 분리해 읽는다.
- 정렬은 `created_at DESC, id DESC` 고정이고, account 쪽은 partial index `idx_notification_inbox_account_visible_cursor`를 사용한다.
- JWT user 쪽은 membership + `notification_user_read_state` left join으로 per-user read/archive/delete 상태를 반영한다.

## Decision Summary

1. 기존 목록 API는 그대로 둔다.
2. 검색/필터는 별도 endpoint `GET /api/v1/notifications/search`로 추가한다.
3. 검색 전용 domain model, use case, read port, JDBC SQL을 기존 목록 경로와 분리한다.
4. 이번 필터는 `readStatus`, `eventType`, `from`, `to`까지만 허용한다.
5. 검색 정렬과 page 방식은 기존과 동일하게 `created_at DESC, id DESC` keyset을 유지한다.
6. free-text 검색은 후속 이슈로 남긴다.

## API Contract

### Endpoint

- `GET /api/v1/notifications/search`

### Query Parameters

- `limit`
  - optional
  - default `20`
  - min `1`, max `100`
- `cursor`
  - optional
  - search 전용 opaque cursor
- `readStatus`
  - optional
  - `ALL | UNREAD | READ`
  - default `ALL`
- `eventType`
  - optional
  - exact match
- `from`
  - optional
  - ISO-8601 UTC instant
- `to`
  - optional
  - ISO-8601 UTC instant

### Time Window Policy

- `from`, `to`는 둘 다 오거나 둘 다 없어야 한다.
- 둘 다 전달되면 `from <= to` 여야 하고, `to - from <= 31일` 이어야 한다.
- 둘 다 없으면 서버가 최근 31일 window를 적용한다.
- 암묵 기본 window를 안정적으로 이어가기 위해 response에 `appliedFrom`, `appliedTo`를 함께 반환한다.
- cursor가 있는 후속 page에서 `from/to`를 다시 주지 않으면, cursor에 저장된 `appliedFrom`, `appliedTo`를 같은 검색 window로 재사용한다.

### Response

검색 전용 response를 따로 둔다.

```json
{
  "items": [
    {
      "notificationId": 123,
      "accountId": 10,
      "eventType": "TransferBooked",
      "title": "급여 입금",
      "message": "4월 급여가 입금되었습니다.",
      "read": false,
      "createdAt": "2026-04-21T09:00:00Z",
      "readAt": null
    }
  ],
  "nextCursor": "opaque",
  "hasNext": true,
  "limit": 20,
  "appliedFrom": "2026-03-21T00:00:00Z",
  "appliedTo": "2026-04-21T00:00:00Z"
}
```

### Error Policy

- `400`
  - 잘못된 `limit`
  - 잘못된 `readStatus`
  - `from`만 있거나 `to`만 있는 경우
  - `from > to`
  - 기간 31일 초과
  - cursor decode 실패
  - cursor 안의 filter fingerprint와 현재 요청 필터 조합 불일치
- `401`
  - 인증 없음
- `200`
  - 결과가 없어도 정상 응답, `items=[]`

## Search Query Model

### New Domain Types

- `NotificationSearchQuery`
  - `limit`
  - `cursor`
  - `readStatus`
  - `eventType`
  - `from`
  - `to`
- `NotificationReadStatusFilter`
  - `ALL`
  - `UNREAD`
  - `READ`
- `NotificationSearchCursor`
  - `createdAt`
  - `id`
  - `filterFingerprint`
- `NotificationSearchSlice`
  - `items`
  - `nextCursor`
  - `hasNext`
  - `limit`
  - `appliedFrom`
  - `appliedTo`

### Why Separate the Query Model

- 기존 `NotificationListQuery`는 최소 목록 page 계약만 표현한다.
- 검색 조건까지 같은 타입에 얹으면 기존 목록 API와 검색 API의 수명주기가 섞인다.
- 별도 query/slice를 두면 이후 text search, facet, 별도 read model 도입 시 검색 경로만 따로 바꿀 수 있다.

## Controller Strategy

- 기존 `NotificationController`에 새 `GET /search` handler를 추가한다.
- 기존 `GET /api/v1/notifications` handler는 그대로 둔다.
- principal 해석은 현재 패턴을 유지한다.
  - `AuthenticatedUserPrincipal` -> user 검색
  - `AuthenticatedAccountPrincipal` -> account 검색
- 검색 response는 기존 `NotificationQueryResponse`와 분리된 `NotificationSearchResponse`로 변환한다.
  - 이유: `appliedFrom`, `appliedTo` 같은 검색 전용 metadata가 필요하다.

## Cursor Strategy

### Ordering

- 정렬은 항상 `created_at DESC, id DESC`
- page 2부터는 기존과 동일한 keyset 조건 사용

```sql
(
  created_at < :cursorCreatedAt
  OR (created_at = :cursorCreatedAt AND id < :cursorId)
)
```

### Filter Fingerprint

- 검색 cursor는 현재 필터 조합을 fingerprint로 함께 저장한다.
- fingerprint 대상:
  - `readStatus`
  - `eventType`
  - `appliedFrom`
  - `appliedTo`
- 다음 page 요청에서 현재 query의 normalized filter와 cursor fingerprint가 다르면 `400`

### Why This Matters

- 검색 중간에 필터를 바꾸면 keyset 의미가 깨진다.
- 목록 API보다 검색 API가 drift 위험이 크므로, cursor에서 조기 차단하는 편이 안전하다.
- `appliedFrom/appliedTo`를 fingerprint에 포함해야 암묵 기본 31일 window도 page 간 동일하게 유지된다.

## Persistence Strategy

### Port Split

- 기존 `NotificationInboxReadPort`는 목록/리플레이/unread count 용도로 유지한다.
- 검색 전용 port를 추가한다.
  - 예: `NotificationInboxSearchPort`

### Use Case Split

- 기존 `NotificationQueryUseCase`는 목록/리플레이/unread count 유지
- 검색 전용 use case 추가
  - 예: `NotificationSearchUseCase`
  - 예: `NotificationSearchService`

### JDBC Adapter Split

- 기존 `JdbcNotificationInboxRepository` 안에 검색 메서드를 추가해도 되지만, 검색 SQL을 목록 SQL과 논리적으로 분리한다.
- 메서드 예시:
  - `searchByUserId(long userId, NotificationSearchQuery query)`
  - `searchByAccountId(long accountId, NotificationSearchQuery query)`

## SQL Strategy

### Account Principal Search

기본 조건:

- `account_id = :accountId`
- `archived_at IS NULL`
- `created_at BETWEEN :from AND :to`

선택 조건:

- `event_type = :eventType`
- `read_at IS NULL` or `read_at IS NOT NULL`

정렬/제한:

- `ORDER BY created_at DESC, id DESC`
- `LIMIT :limitPlusOne`

### JWT User Search

기본 조건:

- membership active
- user active
- `n.archived_at IS NULL`
- `r.archived_at IS NULL`
- `r.deleted_at IS NULL`
- `n.created_at BETWEEN :from AND :to`

선택 조건:

- `n.event_type = :eventType`
- `r.read_at IS NULL` for `UNREAD`
- `r.read_at IS NOT NULL` for `READ`

### Why No Text Search Yet

- `title/message ILIKE`는 현재 schema와 index로는 planner drift 위험이 크다.
- `t3.micro` 기준에서 text search까지 같이 열면 이번 PR의 목적이 “검색 경로 분리”에서 “새 검색 엔진 흉내”로 커진다.
- 먼저 structured filters와 전용 cursor를 분리하는 것이 실무적으로 안전하다.

## Index Strategy

### Keep Existing Indexes

- `idx_notification_inbox_account_visible_cursor`
- `idx_notification_inbox_account_visible_unread`

### Add One Search-Focused Index

우선 1개만 추가한다.

- `idx_notification_inbox_account_event_type_cursor`
  - `(account_id, event_type, created_at DESC, id DESC)`
  - `WHERE archived_at IS NULL`

### Why Only One New Index

- `eventType`는 선택도가 높고 실무 필터 빈도도 높다.
- `readStatus`는 `read_at IS NULL/IS NOT NULL`로 갈라지지만, 이번 PR에서 index를 여러 개 더 만들면 write 비용과 review 범위가 커진다.
- 검색 전용 경로를 먼저 안정화한 뒤, 필요하면 `READ` 쪽 보조 index를 후속으로 여는 편이 낫다.

## Validation Rules

- `limit`: `1..100`
- `readStatus`: enum only
- `eventType`: blank면 null로 정규화
- `from/to`
  - partial 금지
  - `from > to` 금지
  - 최대 31일
- cursor:
  - broken payload 금지
  - filter mismatch 금지

## Backward Compatibility

- 기존 목록 API response shape 유지
- 기존 목록 cursor 유지
- unread count 유지
- single/bulk read/archive/delete 유지
- SSE 유지

즉, 이번 변경은 검색 전용 surface 추가이지 기존 notification inbox API 재설계가 아니다.

## Test Strategy

### Domain / Unit

- `NotificationSearchQuery` 검증
- `NotificationReadStatusFilter` 기본값/파싱
- `from/to` 31일 제한
- search cursor fingerprint 생성/비교

### Controller

- `GET /api/v1/notifications/search` 정상 응답
- invalid `readStatus` -> `400`
- partial `from/to` -> `400`
- 31일 초과 -> `400`
- broken cursor -> `400`
- cursor/filter mismatch -> `400`

### JDBC Integration

account principal:

- 기본 search first page
- `UNREAD`
- `READ`
- `eventType`
- 기간 필터
- cursor next page

JWT user principal:

- membership active 범위만 조회
- per-user read state 반영
- shared account에서 사용자별 `READ/UNREAD` 분리

### API Integration

- 기존 `GET /api/v1/notifications` 회귀 없음
- 새 `/search`가 검색 조건대로 결과를 반환
- 같은 cursor를 다른 filter와 재사용하면 `400`
- unread count/read 처리 계약 회귀 없음

## Operational Notes

- 검색 endpoint는 fail-fast validation으로 넓은 조회를 초기에 차단한다.
- 검색 쿼리는 `limit + 1` 방식으로 `hasNext`를 계산한다.
- 검색 API와 기존 목록 API를 분리해, 이후 text search나 별도 검색 projection이 필요해지면 검색 경로만 교체할 수 있게 한다.

## Follow-Up Candidates

- title/message free-text 검색
- 인기 filter 기준 추가 index 최적화
- eventType별 facet count
- 별도 notification_search_projection 도입 검토
