# Scope

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

## Target

- Pub/Sub 유실 시나리오
- Streams Consumer Group
- ACK / XPENDING / XAUTOCLAIM
- stream trimming

## Out of Scope

- Kafka/Outbox 본격 구현
- 실제 정산 시스템 완성

## Completion Gate

- [ ] Pub/Sub 구독자 부재 유실을 재현했다.
- [ ] Streams consumer 장애 후 재처리를 확인했다.
- [ ] pending message evidence를 저장했다.
- [ ] report에 Pub/Sub과 Streams 선택 기준을 기록했다.
