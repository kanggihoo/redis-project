# Phase 4. Redis Data Structures

## Goal

WePay 도메인에서 Redis 자료구조를 자연스럽게 적용하고 메모리/정확도/응답 시간을 비교한다.

## Targets

- ZSET: 가맹점 거래 랭킹
- HyperLogLog: 일별 활성 거래자 수
- Geo: 주변 가맹점 검색
- MEMORY USAGE / OBJECT ENCODING

## Completion Criteria

- 자료구조별 기능 테스트를 통과한다.
- Set vs HyperLogLog 메모리 차이를 기록한다.
- ZSET/Geo 조회 지연과 Redis memory evidence를 저장한다.

## Phase Docs

- [Phase Hub](../phases/04-data-structures/README.md)
