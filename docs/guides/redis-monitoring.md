# Redis Monitoring Guide

Redis 내부 상태는 redis-cli, redis_exporter, Grafana를 함께 사용해 관측한다.

## Useful Commands

```bash
redis-cli INFO memory
redis-cli INFO stats
redis-cli INFO commandstats
redis-cli SLOWLOG GET 10
redis-cli --bigkeys
redis-cli --hotkeys
```

## Useful Metrics

- keyspace hits/misses
- used memory
- evicted keys
- expired keys
- command latency
- connected clients
