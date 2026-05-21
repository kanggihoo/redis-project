# Phase 8. Troubleshooting

## Goal

Redis가 느리거나 메모리가 부족한 상황을 재현하고 운영 관측 도구로 원인을 찾는다.

## Targets

- SLOWLOG
- Hot Key
- Big Key
- maxmemory / eviction policy
- redis_exporter + Grafana

## Completion Criteria

- slow command, hot key, big key 중 최소 2개 이상을 재현한다.
- eviction policy별 결과를 기록한다.
- Grafana 또는 redis-cli evidence를 저장한다.

## Phase Docs

- [Phase Hub](../phases/08-troubleshooting/README.md)
