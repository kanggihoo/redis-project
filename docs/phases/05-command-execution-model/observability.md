# Observability

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

| Area | Metric | Purpose |
|---|---|---|
| Redis | commandstats | 명령별 호출/지연 |
| App | execution latency | 방식별 소요 시간 |
| App | retry count | WATCH 충돌 비용 |
| k6 | p95 / p99 | 외부 응답 시간 |
