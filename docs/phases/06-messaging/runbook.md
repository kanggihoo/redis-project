# Runbook

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

## Steps

1. Pub/Sub subscriber가 있을 때와 없을 때 publish 결과를 비교한다.
2. Streams에 정산 이벤트를 XADD한다.
3. Consumer Group으로 메시지를 읽고 ACK한다.
4. consumer 장애를 유발하고 XPENDING/XAUTOCLAIM으로 재처리한다.
5. stream trimming 정책을 적용하고 결과를 기록한다.
