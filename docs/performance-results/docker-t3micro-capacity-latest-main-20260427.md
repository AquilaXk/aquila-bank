# docker-t3micro-capacity-latest-main-20260427

## Docker t3.micro Capacity Smoke

- archivedAt: 2026-04-26T18:55:48Z
- capacity smoke status: 0
- source: tools/test/run-docker-t3micro-capacity-smoke.sh
- image: eclipse-temurin:21-jdk
- cpus: 2
- memory: 1024m
- memorySwap: 1024m
- pidsLimit: 384
- repeat: 1
- dbPoolMaxSize: 4
- serverThreadsMax: 16
- sseMaxTotalSessions: 64
- notificationStreamMax: 4

## Notes

- Docker cgroup은 CPU credit, EBS latency, 실제 AWS network를 재현하지 않습니다.
- 실패한 실행도 status와 budget 값을 남겨 다음 재실행 기준으로 사용합니다.
