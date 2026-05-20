# Phase 6. Messaging

## Goal

Pub/Sub과 Streams의 유실/보존/재처리 차이를 단일 서버 환경에서도 명확히 체감한다.

## Targets

- Pub/Sub: 구독자 부재 시 메시지 유실
- Streams: Consumer Group, ACK, XPENDING, XAUTOCLAIM
- Stream trimming

## Completion Criteria

- Pub/Sub 유실 시나리오를 재현한다.
- Streams consumer 장애와 재처리 evidence를 저장한다.
- 정산 이벤트에는 왜 Streams가 더 적합한지 정리한다.

## Phase Docs

- [Phase Hub](../phases/06-messaging/README.md)
