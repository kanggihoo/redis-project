# Phase 1. Cache Aside & TTL

## Goal

계좌 잔액 조회와 카운터성 데이터를 대상으로 Cache Aside, TTL, negative caching, Write Back의 효과와 한계를 측정한다.

## Key Questions

- 캐시 적용 전후 DB query count와 p95/p99는 얼마나 달라지는가?
- TTL이 너무 짧거나 길 때 어떤 문제가 생기는가?
- 존재하지 않는 key 조회에 negative caching이 필요한가?

## Completion Criteria

- cache hit/miss 테스트와 k6 결과를 정리한다.
- DB query 감소율과 cache hit ratio를 기록한다.
- 기존 Phase 1 문서는 `docs/legacy/`에서 참고하되 결과는 phase report에 모은다.

## Phase Docs

- [Phase Hub](../phases/01-cache-aside-ttl/README.md)
