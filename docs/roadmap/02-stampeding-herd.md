# Phase 2. Stampeding Herd

## Goal

캐시 만료 직후 동시 요청이 DB로 몰리는 문제를 재현하고 Redis 기반 완화 전략을 비교한다.

## Strategies

- Mutex Lock
- Logical Expiration
- TTL Jitter

## Completion Criteria

- TTL 만료 직후 DB query 폭증을 재현한다.
- 각 전략의 DB query count, p95/p99, lock wait를 비교한다.
- 기존 Phase 2 문서는 `docs/legacy/`에서 참고하되 결과는 phase report에 모은다.

## Phase Docs

- [Phase Hub](../phases/02-stampeding-herd/README.md)
