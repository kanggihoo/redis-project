# Observability

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

| Area | Metric | Purpose |
|---|---|---|
| App | stale read count | 캐시 불일치 규모 |
| Redis | key TTL / value | 캐시 상태 |
| SQL | DB current value | source of truth 확인 |
| k6 | p95 / p99 | 정합성 전략 비용 |
