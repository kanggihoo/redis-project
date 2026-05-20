# Observability

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

| Area | Metric | Purpose |
|---|---|---|
| Redis | stream length | 이벤트 적재량 |
| Redis | pending entries | 미처리 메시지 |
| App | publish count | 발행 수 |
| App | ack count | 처리 완료 수 |
| App | lost message count | Pub/Sub 유실 체감 |
