# Runbook

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

## Steps

1. Fixed Window limiter를 실행한다.
2. window 경계 시점에 연속 요청을 보내 초과 통과 여부를 확인한다.
3. Sliding Window와 Token Bucket을 같은 조건에서 실행한다.
4. key count, memory usage, block count를 기록한다.
