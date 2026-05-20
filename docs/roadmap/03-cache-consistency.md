# Phase 3. Cache Consistency

## Goal

DB 업데이트와 Redis 캐시 갱신/삭제 사이에서 발생하는 stale data 문제를 재현하고 완화한다.

## Key Questions

- DB 업데이트 후 캐시 삭제 실패 시 사용자는 어떤 값을 보는가?
- delete cache와 update cache는 어떤 트레이드오프가 있는가?
- 캐시 재생성 race는 어떻게 발생하는가?

## Completion Criteria

- stale cache 시나리오를 테스트로 재현한다.
- 캐시 무효화 전략별 결과를 기록한다.
- `report.md`에 Redis 캐시 정합성 기준을 정리한다.

## Phase Docs

- [Phase Hub](../phases/03-cache-consistency/README.md)
