# docker-t3micro-capacity-latest-main-gate-retest-20260427

## Docker t3.micro Capacity Smoke

- archivedAt: 2026-04-27T01:07:27Z
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
- telemetryStats: build/reports/t3micro/docker-t3micro-capacity-latest-main-gate-retest-20260427-docker-stats.tsv
- telemetrySummary: build/reports/t3micro/docker-t3micro-capacity-latest-main-gate-retest-20260427-telemetry.env
- statsSamples=0
- peakCpuPercent=0.00
- peakMemoryMiB=0.00
- peakPids=0
- gcLogPath=build/reports/t3micro/docker-t3micro-capacity-latest-main-gate-retest-20260427-gc.log
- gcLogBytes=2748

## Notes

- Docker cgroup은 CPU credit, EBS latency, 실제 AWS network를 재현하지 않습니다.
- 실패한 실행도 status와 budget 값을 남겨 다음 재실행 기준으로 사용합니다.
