# Runbook

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

## Steps

1. 캐시 TTL을 짧게 설정하고 값을 warm-up한다.
2. TTL 만료 직후 k6 동시 요청을 실행한다.
3. Mutex Lock, Logical Expiration, TTL Jitter를 같은 조건에서 실행한다.
4. DB query count와 p95/p99를 비교한다.
