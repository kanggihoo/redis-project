# Observability

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

| Area | Metric | Purpose |
|---|---|---|
| DB | query count | DB 폭주 여부 |
| Redis | lock wait / key TTL | 완화 전략 동작 확인 |
| k6 | p95 / p99 | 대기 시간 영향 |
| App | cache rebuild count | 캐시 재생성 횟수 |
