# Production Promotion

## Trigger

- Manual: GitHub Actions `Production Promotion` workflow dispatch
  - input `sha`: staging deployment status가 `success`인 40자 main commit SHA
- Tag: `prod-*` tag push
  - tag가 가리키는 commit을 promotion target SHA로 사용

## Guard

- target SHA는 `origin/main`에서 도달 가능해야 합니다.
- target SHA와 같은 staging GitHub deployment가 있어야 합니다.
- 해당 staging deployment의 최신 status가 `success`여야 합니다.
- guard가 실패하면 `production` Environment approval 전 단계에서 workflow가 중단됩니다.

## Approval

- 실제 production hook 호출은 GitHub Environment `production` job에서만 실행됩니다.
- repository settings에서 `production` Environment에 required reviewer를 설정해야 UI 승인 gate가 적용됩니다.
- concurrency group은 `production-promotion`이며, 동시에 두 production 승격이 실행되지 않게 합니다.

## Environment Secrets

- `PRODUCTION_DEPLOY_WEBHOOK_URL`
- `PRODUCTION_DEPLOY_TOKEN`

Secret 값은 저장소에 기록하지 않습니다. workflow는 secret이 없으면 성공으로 위장하지 않고 fail-fast합니다.

## Deploy Hook Payload

```json
{
  "sha": "<target main sha>",
  "repository": "owner/repo",
  "environment": "production",
  "runUrl": "https://github.com/owner/repo/actions/runs/<run-id>",
  "trigger": "workflow_dispatch | push",
  "ref": "<input ref or prod-* tag>"
}
```

## Deployment Status

- workflow는 production GitHub deployment를 생성합니다.
- hook 호출 전 status를 `in_progress`로 기록합니다.
- hook 성공 시 `success`, 실패 시 `failure`로 갱신합니다.
- production 승격 이력은 GitHub deployments와 Actions run URL을 함께 확인합니다.

## Rollback

- 기본 rollback은 `main` 기준 revert PR을 merge한 뒤 새 staging 성공 SHA를 production으로 승격합니다.
- 긴급 재승격이 필요하면 직전 production 성공 SHA가 staging success guard를 통과하는지 먼저 확인합니다.
- main 밖 commit, staging 성공 기록이 없는 commit, 짧은 SHA 입력은 production promotion 대상이 아닙니다.
