# Runbook

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

## Steps

1. 동일한 Redis 작업 묶음을 정의한다.
2. 개별 명령, pipeline, MULTI/EXEC, WATCH, Lua 방식으로 실행한다.
3. latency, round trip, 실패 처리 차이를 기록한다.
