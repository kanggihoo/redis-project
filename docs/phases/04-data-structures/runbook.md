# Runbook

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

## Steps

1. 거래 데이터를 생성해 ZSET ranking을 갱신한다.
2. 동일 사용자 반복 거래를 HLL에 기록한다.
3. 가맹점 좌표를 Geo에 적재하고 주변 검색을 실행한다.
4. `MEMORY USAGE`와 `OBJECT ENCODING` 결과를 저장한다.
