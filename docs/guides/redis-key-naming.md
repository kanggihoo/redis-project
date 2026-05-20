# Redis Key Naming Guide

Key naming은 별도 Phase가 아니라 모든 Redis 실험에서 지켜야 할 공통 규칙이다.

## Pattern

```text
account:{accountId}
account:{accountId}:balance
merchant:ranking:daily
dau:{yyyy-MM-dd}
store:locations
ratelimit:{userId}:{api}
stream:settlement
```

## Rules

- prefix로 기능 영역을 구분한다.
- high cardinality key는 evidence에 key count를 기록한다.
- 운영성 확인에는 `SCAN` 계열을 사용하고 `KEYS`는 피한다.
