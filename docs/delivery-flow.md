# Delivery Flow

## Branch Strategy

- feature, fix, ci 작업은 `main`에서 짧게 분기한 `feature/*`, `fix/*`, `ci/*` 브랜치에서 진행합니다.
- 모든 변경은 GitHub issue를 먼저 만들고 `main` 대상 PR로만 병합합니다.
- 보호 브랜치 직접 push는 금지하고, 리뷰 가능한 한 가지 목적 단위로 PR을 유지합니다.

## CI Gate

- backend/frontend CI는 `main` 대상 PR에서 실행합니다.
- workflow 수정만 있는 PR도 gate가 돌도록 `.github/workflows/**` 변경을 CI path에 포함합니다.

## Main CI/CD

- `main`에 merge된 SHA는 `Main CI/CD` workflow가 backend/frontend check 후 자동 배포합니다.
- 배포 webhook 주소는 repository Actions secret `DEPLOY_WEBHOOK_URL`에 둡니다.
- 인증이 필요한 배포 엔드포인트면 repository Actions secret `DEPLOY_WEBHOOK_TOKEN`을 추가합니다.
- deploy payload는 `sha`, `ref`, `repository`를 포함합니다.
- 별도 staging/prod 승격 단계나 GitHub Environment 승인 단계는 두지 않습니다.

## Feature Flag

- 미완성 기능은 장기 `develop` 브랜치 대신 feature flag로 숨깁니다.
- 프런트 기준선은 `NEXT_PUBLIC_FEATURE_FLAGS` 환경변수로 관리합니다.
- 값은 쉼표 구분 flag key 목록입니다.
- 예시: `NEXT_PUBLIC_FEATURE_FLAGS=ops-console-preview,transfer-reversal`
