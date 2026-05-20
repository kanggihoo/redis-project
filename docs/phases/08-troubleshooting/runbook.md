# Runbook

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

## Steps

1. 느린 명령 또는 큰 자료구조를 만든다.
2. `SLOWLOG GET`, `redis-cli --bigkeys`, hot key 관련 지표를 확인한다.
3. maxmemory와 eviction policy를 바꿔 쓰기 실패/eviction을 확인한다.
4. Grafana evidence와 함께 report를 작성한다.
