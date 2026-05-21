# Tasks

## 진행 예정

1. Phase 3 계획 문서 작성
   - 파일 후보: `docs/PLAN-phase3-Security.md`
   - 포함 내용: Fixed Window, Sliding Window, JWT Blacklist, Session Management, 테스트 전략
   - Phase 2 회고의 개선 규칙을 반영한다.

2. Phase 3 구현 시작
   - Rate Limiting부터 시작한다.
   - Fixed Window의 경계 조건 버그를 먼저 테스트로 증명한다.
   - Sliding Window는 시간 의존성을 `Clock` 추상화로 격리한다.
   - Redis 값이 문자열/카운터이면 `StringRedisTemplate`을 우선 사용한다.

3. JWT Blacklist 구현
   - 토큰 잔여 만료 시간을 TTL로 사용한다.
   - TTL 계산은 Redis 없는 순수 단위 테스트로 분리한다.
   - Redis 저장/조회는 Testcontainers 기반 통합 테스트로 검증한다.

4. Spring Session Redis 검토
   - 현재 `spring-boot-starter-session-data-redis` 의존성은 존재한다.
   - Phase 3 목적에 맞춰 세션 공유 시나리오를 최소 구현으로 정의한다.

## 남은 검증

- Phase 2 K6 비교 부하테스트 실행
  - 명령 후보:
    - `docker compose up -d`
    - `cd wepay; ./gradlew bootRun`
    - `docker compose --profile loadtest run k6`
  - 기록할 수치:
    - baseline, mutexLock, logicalExpiration, ttlJitter별 응답 시간
    - DB 조회 수 또는 관측 가능한 대체 지표
    - 실패율

## 보류

- Next.js BFF 구현
  - README의 장기 아키텍처에는 포함되어 있으나 현재 코드베이스는 Spring Boot API 중심이다.
- Phase 4 이후 동시성/메시징/성능 분석 구현
  - Phase 3 완료 후 진행한다.
