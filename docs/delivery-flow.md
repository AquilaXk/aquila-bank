# Delivery Flow

## Branch Strategy

- feature, fix, ci 작업은 `main`에서 짧게 분기한 `feature/*`, `fix/*`, `ci/*` 브랜치에서 진행합니다.
- 모든 변경은 GitHub issue를 먼저 만들고 `main` 대상 PR로만 병합합니다.
- 보호 브랜치 직접 push는 금지하고, 리뷰 가능한 한 가지 목적 단위로 PR을 유지합니다.

## CI Gate

- backend/frontend CI는 `main` 대상 PR에서 실행합니다.
- `main` push 후에도 같은 gate를 다시 실행해 merge SHA 기준 상태를 확인합니다.
- workflow 수정만 있는 PR도 gate가 돌도록 `.github/workflows/**` 변경을 CI path에 포함합니다.

## Staging Deploy

- `main`에 merge된 SHA는 `Deploy Staging` workflow가 자동 배포합니다.
- `staging` GitHub Environment에 `DEPLOY_WEBHOOK_URL` secret이 반드시 있어야 합니다.
- 인증이 필요한 배포 엔드포인트면 `DEPLOY_WEBHOOK_TOKEN` secret을 같은 environment에 추가합니다.
- staging deploy payload는 `sha`, `ref`, `repository`, `environment`를 포함합니다.

## Production Promote

- production 승격은 `Deploy Production` workflow로만 수행합니다.
- 트리거는 두 가지입니다.
  - `workflow_dispatch`로 staging 성공 SHA를 직접 입력
  - 같은 SHA를 가리키는 `prod-*` 태그 push
- workflow는 승격 전에 다음을 검증합니다.
  - 대상 SHA가 `main`에 포함되어 있는지
  - `staging` environment에서 같은 SHA deploy가 `success` 상태인지
- `production` GitHub Environment에 required reviewers를 설정하면 수동 승인 경로를 강제할 수 있습니다.

## Feature Flag

- 미완성 기능은 장기 `develop` 브랜치 대신 feature flag로 숨깁니다.
- 프런트 기준선은 `NEXT_PUBLIC_FEATURE_FLAGS` 환경변수로 관리합니다.
- 값은 쉼표 구분 flag key 목록입니다.
- 예시: `NEXT_PUBLIC_FEATURE_FLAGS=ops-console-preview,transfer-reversal`
