# Scope

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

## Target

- Stampeding Herd 재현
- Mutex Lock
- Logical Expiration
- TTL Jitter

## Completion Gate

- [ ] TTL 만료 직후 DB query 폭증을 재현했다.
- [ ] 완화 전략별 DB query count를 비교했다.
- [ ] k6와 Redis 지표를 evidence에 저장했다.
- [ ] report에 전략별 선택 기준을 기록했다.
