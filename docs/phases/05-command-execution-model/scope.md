# Scope

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

## Target

- Individual commands
- Pipeline
- MULTI/EXEC
- WATCH
- Lua

## Out of Scope

- 송금 도메인 동시성 본격 해결
- Redisson Lock 깊은 비교

## Completion Gate

- [ ] 동일 작업을 각 방식으로 실행했다.
- [ ] latency와 round trip 차이를 기록했다.
- [ ] WATCH 충돌과 Lua 원자성 차이를 설명했다.
- [ ] report에 명령 실행 모델 선택 기준을 기록했다.
