# Notification Channel Provider Real Delivery Design

**Issues:** `#288`, `#289`
**Branches:** `feat/notification-real-channel-provider`, `perf/notification-provider-delivery-smoke`

## Goal

notification channel outbox worker가 Logging fallback만 쓰는 상태를 끝내고, 실무에서 가장 흔한 outbound webhook 방식으로 EMAIL/SMS 실제 delivery 경계를 연다. 이어서 같은 경계에 대해 작은 timeout, 작은 batch, bounded backoff 기준을 고정하는 smoke를 추가한다.

## Non-Goals

- vendor SDK 직접 도입
- contact schema 또는 verified contact 모델 추가
- notification inbox API/SSE API 계약 변경
- Kafka topic/payload schema 변경
- 외부 provider 재시도 큐를 별도 신설

## Current State

- `NotificationChannelProviderPort` 구현은 `LoggingNotificationChannelProvider` 하나뿐이다.
- worker는 `claim -> provider send -> SENT/FAILED/backoff/quarantine` 상태 전이만 구현돼 있다.
- outbox row에는 destination이 없고 `userId`, `channel`, `payload`만 있다.
- 현재 저장소에는 verified email/phone 컬럼이 없고 `bank_user.login_id`만 공통 사용자 식별자로 존재한다.

## Decision Summary

1. 실제 provider adapter는 vendor SDK 대신 channel별 webhook JSON POST로 구현한다.
2. `userId -> loginId` lookup은 notification domain 전용 port로 추가하고 global persistence adapter가 `bank_user`를 조회한다.
3. destination 해석은 `loginId` 형식 기반으로만 제한한다.
4. EMAIL channel은 email loginId만, SMS channel은 E.164 phone loginId만 발송한다.
5. destination 미해석, lookup miss, channel URL 누락은 fail-safe skip으로 처리해 outbox를 `SENT`로 정리한다.
6. 실제 provider 장애만 예외로 전파해 기존 retry/backoff/quarantine 계약을 그대로 사용한다.
7. smoke는 외부 SaaS 대신 local mock endpoint로 success/timeout/backoff를 검증한다.

## Architecture

### Domain Boundary

- `NotificationChannelProviderPort`는 그대로 유지한다.
- 새 `NotificationChannelRecipientLookupPort`가 `userId` 기준 delivery 대상 식별자 조회만 담당한다.
- notification domain은 loginId 저장 방식, HTTP client, Spring `RestClient`를 모른다.

### Global Adapter

- `WebhookNotificationChannelProvider`
  - `NotificationChannelRecipientLookupPort`로 loginId 조회
  - `NotificationChannelDeliveryDestinationResolver`로 EMAIL/E.164 판별
  - channel별 URL과 timeout 설정으로 JSON POST 호출
- `LoggingNotificationChannelProvider`
  - `notification.channel-provider.delivery.enabled=false`일 때 fallback 유지

### Provider Request

provider body는 이미 저장된 outbox payload를 재사용하되 실무 운영에 필요한 필드만 다시 구성한다.

```json
{
  "channel": "EMAIL",
  "deliveryKey": "evt-transfer-1",
  "notificationId": 101,
  "userId": 7,
  "accountId": 33,
  "category": "TRANSACTIONAL",
  "eventType": "TransferBooked",
  "destination": "alice@example.com",
  "title": "이체 완료",
  "message": "홍길동님에게 10,000원을 보냈습니다.",
  "createdAt": "2026-04-23T09:00:00Z"
}
```

- `deliveryKey`는 기존 `eventKey`를 그대로 사용한다.
- 공통 auth header는 env로 주입한다.
- raw secret, provider URL, 추가 개인정보는 payload/로그에 남기지 않는다.

## Failure Policy

- loginId lookup miss: warn 로그 후 skip
- loginId 형식 미지원: warn 로그 후 skip
- EMAIL/SMS channel과 destination 형식 불일치: warn 로그 후 skip
- 해당 channel URL 누락: warn 로그 후 skip
- webhook timeout / 5xx / network error: 예외 전파로 worker `FAILED` + bounded backoff
- max retry 도달: 기존처럼 `QUARANTINED`

skip을 정상 반환으로 두는 이유는 잘못된 외부 발송을 막으면서도 같은 row가 무한 재시도되지 않게 하기 위해서다.

## Configuration

```yaml
notification:
  channel-provider:
    delivery:
      enabled: false
      auth-header-name: Authorization
      auth-header-value: ""
      connect-timeout-ms: 3000
      read-timeout-ms: 5000
      email:
        url: ""
      sms:
        url: ""
```

- `enabled=false`: logging fallback
- `enabled=true`: 실제 webhook provider adapter
- channel별 URL은 독립적으로 비워둘 수 있고, 비어 있으면 그 channel만 skip 된다.

## Testing Strategy

### Feature

- provider adapter unit test
  - EMAIL success
  - SMS success
  - loginId 형식 미지원 skip
  - channel mismatch skip
  - channel URL 누락 skip
  - provider error 전파
- configuration test
  - 기본값 logging fallback
  - `delivery.enabled=true`일 때 webhook provider wiring
- worker 회귀
  - provider 정상 반환 시 `SENT`
  - provider 예외 시 `FAILED` + bounded backoff

### Perf Smoke

- local mock endpoint 기반 smoke
  - EMAIL provider `202 Accepted`
  - SMS provider read timeout
  - retry 후 `FAILED` `available_at` 증가 확인
- script는 작은 batch와 짧은 timeout을 echo 해 drift를 먼저 드러낸다.

## Delivery Plan

1. feature branch에서 design spec과 실제 provider adapter를 구현한다.
2. perf branch는 feature branch tip 위 stacked branch로 만들고 smoke만 추가한다.
3. 두 번째 PR 본문에 첫 번째 PR 의존성을 명시한다.

## Risks

- `login_id`를 delivery destination으로 재사용하는 구조라 verified contact가 없는 계정은 skip 될 수 있다.
- webhook timeout이 너무 크면 worker thread가 길게 점유된다.
- stacked PR이라 두 번째 PR 리뷰 시 선행 PR commit을 함께 보게 될 수 있다.

## Follow-Up

- verified contact 모델 도입
- provider response code별 richer metric/alert
- bounded scheduled smoke 또는 CI gate 연동
