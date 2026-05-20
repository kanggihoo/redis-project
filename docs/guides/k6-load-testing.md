# k6 Load Testing Guide

k6는 Redis 적용 전후의 DB 부하, 응답 시간, 실패율을 비교하기 위해 사용한다.

## Required Values

- scenario name
- VU / duration
- RPS
- p95 / p99
- error rate
- Redis hit/miss 또는 command latency
- DB query count, when relevant

## Evidence Path

```text
docs/evidence/<phase>/k6/
```
