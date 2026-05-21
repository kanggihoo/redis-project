# Observability

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

## Aggregated Metrics

| Metric | Purpose |
|---|---|
| cache hit ratio | 캐싱 효과 |
| DB query count | DB 부하 감소 |
| Redis memory usage | 메모리 비용 |
| p95 / p99 | 사용자 지연 |
| command latency | Redis 병목 |
| blocked/allowed count | rate limiting 효과 |
| pending entries | Streams 장애 복구 상태 |
