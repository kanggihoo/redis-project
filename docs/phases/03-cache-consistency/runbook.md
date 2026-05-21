# Runbook

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

## Steps

1. 계좌 정보를 캐시에 적재한다.
2. DB 값을 변경하고 캐시 삭제를 생략하거나 실패시킨다.
3. 조회 API가 오래된 값을 반환하는지 확인한다.
4. delete cache와 update cache 전략을 각각 실행한다.
5. stale read count와 응답 시간을 비교한다.
