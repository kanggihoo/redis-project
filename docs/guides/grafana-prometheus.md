# Grafana and Prometheus Guide

Grafana와 Prometheus는 Redis, Spring, PostgreSQL, k6 지표를 같은 시간축에서 비교하기 위해 사용한다.

## Recommended Panels

- Redis keyspace hits/misses
- Redis used memory
- Redis command latency
- Redis evicted/expired keys
- k6 RPS and p95/p99
- DB query count
- JVM and Hikari metrics

## Evidence

Screenshot은 각 Phase의 `docs/evidence/<phase>/grafana/` 또는 해당 실험 디렉터리에 저장한다.
