# Customer Application Mock Webhook

개인 프로젝트 웹뱅킹 범위에서 고객 신청은 외부 기관이나 vendor 연동을 구현하지 않습니다. 공과금, 오픈뱅킹, 인증서, 보안매체, FX 같은 업무는 신청 접수와 운영자 처리 상태 전이를 보여주고, 외부 실행 결과는 mock/webhook boundary로 수동 반영합니다.

## Ops Console

프런트 운영 콘솔은 `NEXT_PUBLIC_OPS_CONSOLE_ENABLED=true`일 때만 노출됩니다.

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 \
NEXT_PUBLIC_OPS_CONSOLE_ENABLED=true \
yarn --cwd front dev
```

`/ops` 화면에서 다음 확인 문구를 입력해야 write action을 실행합니다.

| Action | Endpoint | Confirm phrase |
| --- | --- | --- |
| review | `/internal/api/v1/customer-service/applications/{applicationReference}/review` | `REVIEW APPLICATION` |
| approve | `/internal/api/v1/customer-service/applications/{applicationReference}/approve` | `APPROVE APPLICATION` |
| reject | `/internal/api/v1/customer-service/applications/{applicationReference}/reject` | `REJECT APPLICATION` |
| cancel | `/internal/api/v1/customer-service/applications/{applicationReference}/cancel` | `CANCEL APPLICATION` |
| execute | `/internal/api/v1/customer-service/applications/{applicationReference}/execute` | `EXECUTE APPLICATION` |
| external callback | `/internal/api/v1/customer-service/applications/{applicationReference}/external-callback` | `CALLBACK APPLICATION` |

## Sample Requests

`INTERNAL_TOKEN`은 로컬 개발용 internal service token을 shell 환경 변수로 주입합니다. 저장소에 token 값을 기록하지 않습니다.

```bash
curl -X POST \
  "http://localhost:8080/internal/api/v1/customer-service/applications/APP-DEMO-1/review" \
  -H "Authorization: Bearer ${INTERNAL_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"reason":"demo review"}'
```

```bash
curl -X POST \
  "http://localhost:8080/internal/api/v1/customer-service/applications/APP-DEMO-1/approve" \
  -H "Authorization: Bearer ${INTERNAL_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"reason":"demo approve"}'
```

```bash
curl -X POST \
  "http://localhost:8080/internal/api/v1/customer-service/applications/APP-DEMO-1/execute" \
  -H "Authorization: Bearer ${INTERNAL_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"reason":"demo execute"}'
```

외부 연동형 업무는 executor가 mock/webhook boundary에서 대기하거나 실패 상태로 남을 수 있습니다. demo 성공 결과를 반영하려면 external callback endpoint를 호출합니다.

```bash
curl -X POST \
  "http://localhost:8080/internal/api/v1/customer-service/applications/APP-DEMO-1/external-callback" \
  -H "Authorization: Bearer ${INTERNAL_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "success": true,
    "reason": "demo callback success",
    "payload": {
      "provider": "mock",
      "externalReference": "MOCK-20260514-0001"
    }
  }'
```

실패 결과는 `success=false`와 reason으로 반영합니다.

```bash
curl -X POST \
  "http://localhost:8080/internal/api/v1/customer-service/applications/APP-DEMO-1/external-callback" \
  -H "Authorization: Bearer ${INTERNAL_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "success": false,
    "reason": "demo provider rejected",
    "payload": {
      "provider": "mock"
    }
  }'
```

## Boundary

- 이 문서는 demo/mock 실행 경계만 다룹니다.
- 실제 공과금 납부, 오픈뱅킹 연결, 인증기관, SMS/EMAIL, 지급결제망 vendor 연동을 구현하지 않습니다.
- callback HMAC/signature, 정산, 보상, reconciliation은 개인 프로젝트 범위 밖입니다.
