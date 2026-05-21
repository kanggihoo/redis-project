# Phase 5. Command Execution Model

## Goal

Redis 명령 실행 방식별 latency, round trip, 원자성 차이를 비교한다.

## Targets

- Individual commands
- Pipeline
- MULTI/EXEC
- WATCH
- Lua

## Completion Criteria

- 동일 작업을 각 방식으로 실행하고 latency를 비교한다.
- WATCH 충돌과 Lua 원자성 차이를 설명한다.
- 동시성 프로젝트와 겹치지 않도록 Redis 명령 모델 관점으로 정리한다.

## Phase Docs

- [Phase Hub](../phases/05-command-execution-model/README.md)
