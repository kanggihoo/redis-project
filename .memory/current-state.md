# Current State

## 현재 상태

- 날짜: 2026-05-08
- 구현 단계: Phase 2까지 구현 완료
- 현재 주 작업축: Phase 3 보안 및 트래픽 제어 진입 준비
- 메모리 생성 기준 자료:
  - `README.md`
  - `docs/PLAN-phase1-caching.md`
  - `docs/PLAN-phase2-Stampeding.md`
  - `docs/phase1-troubleshooting.md`
  - `docs/phase2-troubleshooting.md`
  - `docs/phase2-retrospective.md`
  - `wepay/build.gradle`
  - 실제 파일 목록

## Phase 1 완료 범위

- Cache Aside: `AccountCacheServiceImpl`
- Write Back: `TransactionCounterServiceImpl`
- Null Caching: `null:account:{id}` 마커
- Cache Invalidation: `AccountCacheEvictEvent` + `@TransactionalEventListener(AFTER_COMMIT)`
- TTL 실험: `cache.account.ttl-seconds`
- Stampeding Herd 재현: `StampedingHerdTest`

## Phase 2 완료 범위

- Stampeding Herd 대응 전략:
  - `MutexLockCacheServiceImpl`
  - `LogicalExpirationCacheServiceImpl`
  - `TtlJitterCacheServiceImpl`
- Logical Expiration 래퍼:
  - `AccountCacheWrapper`
  - `CacheWrapper` 관련 테스트 흔적 존재
- REST API:
  - `AccountController`
  - `StoreController`
- Redis 특수 자료구조:
  - ZSET: `StoreRankingServiceImpl`
  - HyperLogLog: `DailyActiveUserServiceImpl`
  - Geo: `StoreLocationServiceImpl`
- K6:
  - `load-tests/stampeding-herd.js`
  - `docker-compose.yml`의 `k6` profile

## 바로 볼 파일

- Phase 3 시작 전:
  - `docs/phase2-retrospective.md`
  - `README.md`의 Phase 3 섹션
  - `wepay/src/main/resources/application.yml`
  - `wepay/src/test/resources/application.yml`
- Rate Limiting 구현 시:
  - `wepay/src/main/java/com/example/wepay/service/*CacheService*.java`
  - `wepay/src/main/java/com/example/wepay/controller/AccountController.java`
  - `wepay/src/test/java/com/example/wepay/controller/*ControllerTest.java`
- Redis 설정 확인 시:
  - `wepay/src/main/java/com/example/wepay/config/RedisConfig.java`
  - `docker-compose.yml`

## 주의할 점

- PowerShell에서 한글 파일을 읽을 때 `Get-Content -Encoding UTF8`을 사용한다.
- 세션 시작 시 전체 소스 탐색이나 테스트 실행을 기본으로 하지 않는다.
- Phase 2 K6 비교 부하테스트는 스크립트와 compose 설정은 있으나 결과 수치 기록이 아직 남아 있다.
