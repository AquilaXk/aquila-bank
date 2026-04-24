# Performance Result Archive

성능 테스트 결과는 실행 단위마다 Markdown으로 남깁니다.

## 원칙

- k6, Docker t3.micro smoke, staging replay, transaction regression gate 결과는 사람이 다시 읽을 수 있는 Markdown 요약을 남깁니다.
- 원본 JSON/latency sample은 `build/reports/**`에 두고, 리뷰/공유용 요약은 이 디렉터리에 둡니다.
- token, JWT, 운영 URL, 개인 식별자는 결과 문서에 기록하지 않습니다.
- 실패한 실행도 원인과 마지막 확인 지점을 문서화합니다.

## 파일명

```text
YYYY-MM-DD-<environment>-<workload>.md
```

예시:

```text
2026-04-25-docker-t3micro-local-smoke.md
2026-04-25-local-loadtest-transaction-100m.md
```

## k6 1억 건 거래 조회

`tools/test/run-k6-transaction-100m-loadtest.sh`는 k6 summary Markdown을 생성한 뒤 기본적으로 이 디렉터리에 복사합니다.

실행 결과:

- [transaction-100m-small-smoke-summary.md](transaction-100m-small-smoke-summary.md)
- [transaction-100m-local-t3micro-20260425-bottleneck.md](transaction-100m-local-t3micro-20260425-bottleneck.md)

로컬 DB에 1억 건 synthetic read model을 먼저 적재하고 k6까지 이어서 실행하는 표준 경로는 아래 명령입니다.

```bash
SEED_TOTAL_ROWS=100000000 \
SEED_TRUNCATE=true \
tools/test/run-transaction-read-model-100m-k6-local.sh
```

기본값은 `SEED_INDEX_STRATEGY=required`입니다. 이 전략은 hot/archive account cursor index를 빈 table 상태에서 먼저 유지해, t3.micro PostgreSQL에서 5천만 row btree를 사후 build하다 OOM 나는 경로를 피합니다.

`SEED_INDEX_STRATEGY=rebuild-all`은 전체 secondary filter index를 drop 후 재생성합니다. 이 모드는 partition/chunk 구조 검증 또는 더 큰 memory budget에서만 사용하고, t3.micro 측정 경로에서는 기본값으로 사용하지 않습니다.

실행 전 확인값:

- local disk 여유 공간
- Docker Desktop memory/disk limit
- `compose.loadtest.yml`의 backend `2 vCPU / 1GiB` budget
- PostgreSQL container가 이전 실행에서 `OOMKilled=true`로 남아 있지 않은지 여부
- hot/cold account와 기간 기본값이 테스트 의도와 맞는지 여부

수동 보관이 필요하면 아래 명령을 사용합니다.

```bash
tools/test/archive-k6-transaction-100m-result.sh \
  build/reports/k6/<name>-summary.md \
  build/reports/k6/<name>-summary.json
```
