# Transfer Partial Reversal Design

**Issue:** `#164`
**Branch:** `feat/transfer-partial-reversal`

## Goal

기존 full reversal 경로를 확장해 원본 송금 금액 중 일부만 reversal 할 수 있게 한다.
원본 ledger row는 수정하지 않고 reversal ledger entry를 누적 append 하며, snapshot/read model/outbox/idempotency는 기존 write transaction 경계 안에서 함께 처리한다.

## Non-Goals

- bulk reversal
- 운영 승인 workflow
- frontend 송금 UI
- notification consumer 신규 fan-out 변경
- reversal 이후 자동 재송금
- 외부 저장소나 분산 lock 도입

## Decision Summary

1. 기존 endpoint `POST /api/v1/transfers/{transactionReference}/reversal`를 유지한다.
2. request body에 선택 필드 `amountMinor`를 추가한다.
3. `amountMinor`가 없으면 남은 reversal 가능 금액 전액을 reversal한다.
4. `amountMinor`가 있으면 `0 < amountMinor <= 남은 reversal 가능 금액`만 허용한다.
5. 원본 1건에 여러 `transfer_reversal` row를 허용한다.
6. 원본 read model 상태는 누적 금액에 따라 `PARTIALLY_REVERSED` 또는 `REVERSED`로 전이한다.
7. reversal outbox payload에는 실제 reversal 금액을 넣는다.

## API Contract

### Request

```json
{
  "sourceAccountId": 101,
  "amountMinor": 500,
  "reversalReason": "CORRECTION",
  "summary": "partial correction"
}
```

- `amountMinor`
  - optional
  - null이면 남은 reversal 가능 금액 전체
  - 양수만 허용

### Response

기존 response shape를 유지하고 `amountMinor`는 실제 reversal 금액을 반환한다.

```json
{
  "originalTransactionReference": "TRX-1",
  "reversalTransactionReference": "TRX-2",
  "sourceAccountId": 101,
  "targetAccountId": 202,
  "amountMinor": 500,
  "currencyCode": "KRW",
  "availableBalanceAfterMinor": 9500,
  "bookedAt": "2026-04-21T00:00:00Z",
  "status": "PARTIALLY_REVERSED"
}
```

## Data Model

### Migration

- `transaction_read_model.transaction_status` check constraint에 `PARTIALLY_REVERSED` 추가
- `transfer_reversal.original_transaction_reference` unique 제약 제거
- `transfer_reversal`에 `(original_transaction_reference, created_at DESC, id DESC)` index 추가

### Remaining Amount

```
remaining = original.amount_minor - SUM(transfer_reversal.amount_minor WHERE original_transaction_reference = ?)
```

계산은 original ledger rows와 `transfer_reversal` rows를 같은 DB transaction 안에서 `FOR UPDATE`로 잠근 뒤 수행한다.

## Write Flow

1. idempotency key insert 또는 재사용
2. original transfer ledger row 2건 lock
3. original transfer의 기존 reversal rows lock
4. remaining amount 계산
5. requested amount 결정
6. target/source snapshot lock
7. target 잔액 검증
8. reversal ledger entry 2건 append
9. snapshot 2건 update
10. original read model 상태를 `PARTIALLY_REVERSED` 또는 `REVERSED`로 update
11. reversal read model row 2건 insert
12. transfer_reversal row insert
13. TransferReversed outbox insert
14. idempotency complete

## Error Policy

- `400`
  - `amountMinor <= 0`
  - request validation 실패
- `404`
  - sourceAccountId와 original transaction이 맞지 않음
- `409`
  - idempotency key가 다른 request fingerprint로 재사용됨
  - same command in progress
  - target balance 부족
  - requested amount가 remaining amount를 초과함

## Tests

- domain command test: null amount는 허용, non-positive amount는 거부, fingerprint에 amount 포함
- controller test: amountMinor가 use case command로 전달됨
- API integration test:
  - partial reversal 후 ledger/read model/outbox/snapshot 검증
  - 두 번째 partial reversal로 전액 도달 시 `REVERSED` 전이
  - remaining 초과 reversal은 `409`
  - 기존 full reversal request는 남은 전액 reversal 유지

## Risks

- 기존 unique 제약 제거 시 중복 full reversal 차단이 누적 금액 검증으로 완전히 대체되어야 한다.
- original read model 상태가 `BOOKED -> PARTIALLY_REVERSED -> REVERSED`로 전이되어 transaction 조회 필터와 detail 응답이 새 상태를 허용해야 한다.
- notification consumer는 기존 `TransferReversed` 이벤트를 계속 처리하되 부분/전체 의미를 amount 기준으로만 구분한다.
