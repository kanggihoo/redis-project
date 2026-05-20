# Redis Lab Roadmap Overview

WePay는 간편송금/정산 도메인을 사용해 Redis의 캐싱, 자료구조, 명령 실행 모델, 메시징, rate limiting, 운영 관측을 실험하는 Redis 전문 학습 프로젝트다.

## Boundary

이 프로젝트에서 Redis는 주인공이다. 인증 보안, PG 결제 장애, Kafka/Outbox, DB 최적화는 별도 프로젝트로 넘긴다.

## Phase Order

| Phase | Name | Main Question |
|---:|---|---|
| 1 | Cache Aside & TTL | 캐시가 DB 부하와 응답 시간에 어떤 영향을 주는가? |
| 2 | Stampeding Herd | 캐시 만료 순간 DB 폭주를 어떻게 막는가? |
| 3 | Cache Consistency | DB와 Redis 캐시 불일치를 어떻게 재현하고 줄이는가? |
| 4 | Data Structures | ZSET, HyperLogLog, Geo는 어떤 문제에 적합한가? |
| 5 | Command Execution Model | 개별 명령, pipeline, MULTI/EXEC, WATCH, Lua는 무엇이 다른가? |
| 6 | Messaging | Pub/Sub과 Streams는 유실/보존 관점에서 어떻게 다른가? |
| 7 | Rate Limiting | Fixed, Sliding, Token Bucket은 어떤 트레이드오프가 있는가? |
| 8 | Troubleshooting | slowlog, hot key, big key, eviction을 어떻게 찾고 해석하는가? |
| 9 | Final Report | Redis 기능별 선택 기준을 어떻게 정리할 것인가? |

## Progress Rule

각 Phase는 `docs/phases/<phase>/report.md`에 재현 결과, 측정 evidence, 선택 기준이 기록되기 전까지 완료로 보지 않는다.
