# Runbook

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

## Steps

1. 테스트 데이터를 초기화한다.
2. 캐시 없는 잔액 조회 기준선을 측정한다.
3. Cache Aside 적용 후 같은 조건으로 측정한다.
4. TTL과 negative caching 시나리오를 실행한다.
5. Write Back counter flush 결과를 확인한다.
6. 결과를 evidence에 저장하고 report를 갱신한다.
