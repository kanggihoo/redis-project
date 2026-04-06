# Phase 1 — 기본 캐싱 전략

## Goal

RedisTemplate을 직접 다루며 Cache Aside / Write-Back / Null Caching / Cache Invalidation을 구현하고,
각 문제를 TDD(Testcontainers) 방식으로 검증한다.

## TDD 규칙

1. **껍데기 먼저** → 2. **실패 테스트(Red)** → 3. **최소 구현(Green)** → 4. **리팩토링**

- 구현 없이 테스트 먼저 작성 금지 (import 에러 = Red 아님)
- 각 단계마다 실행해서 결과 눈으로 확인

---

### 버전

- Spring Boot: `4.0.5`
- Java: `21`

## Tasks

### 0. 인프라 세팅

- [ ] `docker-compose.yml` 작성 → Verify: `docker compose up` 후 모든 컨테이너 healthy
  - PostgreSQL 17-alpine
  - Redis 7.2-alpine
  - redis_exporter
  - Prometheus
  - Grafana (Redis 대시보드 ID: 11835 import)
- [ ] Spring Boot 프로젝트 기본 세팅 → Verify: `./gradlew bootRun` 정상 기동
  - `spring-boot-starter-data-redis`
  - `redisson`
  - `testcontainers-redis`
  - `micrometer-registry-prometheus`
- [ ] `RedisConfig` 작성 → Verify: Redis 연결 확인 (StringRedisTemplate + RedisTemplate<String, Object> 두 개 Bean 등록)
- [ ] DB 스키마 + Seed 데이터 → Verify: `accounts` 테이블 10개 row 확인

---

### 1. Cache Aside — 잔액 조회 캐싱

**시나리오:** `GET /api/accounts/{id}` 호출 시 Redis 먼저 확인, 없으면 DB 조회 후 캐시 저장

- [ ] `AccountCacheService` 껍데기 작성 → Verify: 컴파일 성공
  ```
  getAccount(Long id): Account
  evictAccount(Long id): void
  ```
- [ ] [Red] 캐시 히트 시 DB 호출 안 하는 테스트 작성 → Verify: 테스트 실패 확인
- [ ] [Green] `GET` / `SET EX` 로 최소 구현 → Verify: 테스트 통과
- [ ] [Red] 캐시 미스 시 DB 조회 후 Redis 저장 테스트 → Verify: 실패 확인
- [ ] [Green] 구현 → Verify: 테스트 통과
- [ ] Redis 키 전략 확정: `account:{id}` / TTL 60초 / JSON 직렬화

---

### 2. Write-Back — 거래 카운터

**시나리오:** 거래 발생 시 `INCR`으로 카운터 증가, 1분 주기 배치로 DB flush

- [ ] `TransactionCounterService` 껍데기 작성 → Verify: 컴파일 성공
  ```
  increment(Long storeId): void
  flushToDB(): void
  ```
- [ ] [Red] `INCR` 호출 테스트 (StringRedisTemplate) → Verify: 실패 확인
- [ ] [Green] 구현 → Verify: 테스트 통과
- [ ] [Red] `@Scheduled` 배치 flush → DB 벌크 업데이트 테스트 → Verify: 실패 확인
- [ ] [Green] 구현 → Verify: 테스트 통과
- [ ] Redis 키 전략: `store:txcount:{storeId}` / String 타입

---

### 3. Null Caching — Cache Penetration 방지

**시나리오:** 존재하지 않는 계좌 반복 조회 시 매번 DB 히트 방지

- [ ] [Red] 없는 계좌 조회 시 DB 1회만 호출되는 테스트 → Verify: 실패 확인
- [ ] [Green] `"NULL"` 마커 캐싱으로 구현 (TTL 30초) → Verify: 테스트 통과
- [ ] 정상 계좌 생성 후 Null 캐시 무효화 테스트 추가

---

### 4. Cache Invalidation — 트랜잭션 버그 재현 및 해결

**시나리오:** 잔액 변경 후 캐시 삭제 타이밍 오류 → 낡은 데이터 노출

- [ ] [Red] 트랜잭션 커밋 전 캐시 삭제 버그 테스트 → Verify: 실패 확인
  - 잔액 업데이트 중 캐시 삭제 → 다른 요청이 구버전 데이터 캐싱
- [ ] 버그 재현 확인 → Verify: 테스트 통과 (버그가 있다는 증명)
- [ ] [Green] `@TransactionalEventListener(AFTER_COMMIT)` 으로 수정 → Verify: 버그 테스트 실패, 정상 테스트 통과

---

### 5. TTL 실험 — 트레이드오프 체감

**시나리오:** TTL 길이에 따라 Hit Ratio vs 정합성이 어떻게 달라지는가

- [ ] TTL 10초 / 60초 / 300초 구간별 테스트 작성
- [ ] 각 TTL에서 잔액 변경 후 캐시 값 확인 → 정합성 오차 시간 측정
- [ ] 결과를 주석으로 정리 (정답 없는 트레이드오프 기록)

---

### 6. Stampeding Herd 현상 재현 (Phase 2 씨앗)

**시나리오:** TTL 만료 직후 다수 요청 → DB 쿼리 폭증 현상 눈으로 확인

- [ ] TTL 5초 설정 후 Testcontainers 환경에서 20 스레드 동시 조회
- [ ] DB 호출 횟수 카운팅 → 20회 발생 확인
- [ ] 주석으로 기록: "Phase 2에서 Mutex Lock / Logical Expiration으로 해결"

---

### 7. 통합 검증

- [ ] Testcontainers 기반 전체 통합 테스트 실행 → Verify: 전체 Green
- [ ] `docker compose up` 후 Grafana 대시보드에서 Cache Hit Ratio 확인
- [ ] Redis CLI로 키 구조 확인: `redis-cli keys "*"` → 키 네이밍 규칙 검증

---

## Done When

- [ ] 전체 테스트 Green
- [ ] Grafana에서 `cache.hit.ratio` 메트릭 확인 가능
- [ ] Stampeding Herd 현상 로그로 확인, Phase 2 문제 정의 주석 작성 완료

## 키 네이밍 규칙

```
account:{id}              → JSON  / TTL 60s  (잔액 캐시)
store:txcount:{storeId}   → String / TTL 없음 (배치 flush)
null:account:{id}         → String / TTL 30s  (Null 마커)
```

## RedisTemplate 설정 방향

```
StringRedisTemplate          → INCR, EXPIRE 등 카운터용
RedisTemplate<String,Object> → JSON 직렬화 객체 캐싱용
  └── GenericJackson2JsonRedisSerializer
```
