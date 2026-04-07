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

- [x] `docker-compose.yml` 작성 → Verify: `docker compose up` 후 모든 컨테이너 healthy
  - PostgreSQL 17-alpine
  - Redis 7.2-alpine
  - redis_exporter
  - Prometheus
  - Grafana (Redis 대시보드 ID: 11835 import)
- [x] Spring Boot 프로젝트 기본 세팅 → Verify: `./gradlew bootRun` 정상 기동
  - `spring-boot-starter-data-redis`
  - `redisson`
  - `testcontainers-redis`
  - `micrometer-registry-prometheus`
- [x] `RedisConfig` 작성 → Verify: Redis 연결 확인 (StringRedisTemplate + RedisTemplate<String, Object> 두 개 Bean 등록)
- [x] DB 스키마 + Seed 데이터 → Verify: `accounts` 테이블 10개 row 확인

---

### 1. Cache Aside — 잔액 조회 캐싱

**시나리오:** `GET /api/accounts/{id}` 호출 시 Redis 먼저 확인, 없으면 DB 조회 후 캐시 저장

- [x] `AccountCacheService` 껍데기 작성 → Verify: 컴파일 성공
  ```
  getAccount(Long id): Account
  evictAccount(Long id): void
  ```
- [x] [Red] 캐시 히트 시 DB 호출 안 하는 테스트 작성 → Verify: 테스트 실패 확인
- [x] [Green] `GET` / `SET EX` 로 최소 구현 → Verify: 테스트 통과
- [x] [Red] 캐시 미스 시 DB 조회 후 Redis 저장 테스트 → Verify: 실패 확인
- [x] [Green] 구현 → Verify: 테스트 통과
- [x] Redis 키 전략 확정: `account:{id}` / TTL 60초 / JSON 직렬화

✅ Task 1: Cache Aside 완료
단계 결과
껍데기 작성 (Account, AccountRepository, AccountCacheService) ✅ 컴파일 성공
[Red] 캐시 히트/미스 테스트 ✅ 실패 확인 (NoSuchBean)
[Green] AccountCacheServiceImpl 구현 ✅ 2/2 테스트 통과
키 전략: account:{id} / TTL 60초 / JSON 직렬화

구현 위치:

Account.java
AccountCacheServiceImpl.java
AccountCacheServiceTest.java

---

### 2. Write-Back — 거래 카운터

**시나리오:** 거래 발생 시 `INCR`으로 카운터 증가, 1분 주기 배치로 DB flush

- [x] `TransactionCounterService` 껍데기 작성 → Verify: 컴파일 성공
  ```
  increment(Long storeId): void
  flushToDB(): void
  ```
- [x] [Red] `INCR` 호출 테스트 (StringRedisTemplate) → Verify: 실패 확인
- [x] [Green] 구현 → Verify: 테스트 통과
- [x] [Red] `@Scheduled` 배치 flush → DB 벌크 업데이트 테스트 → Verify: 실패 확인
- [x] [Green] 구현 → Verify: 테스트 통과
- [x] Redis 키 전략: `store:txcount:{storeId}` / String 타입

✅ Task 2: Write-Back 완료
| 단계 | 결과 |
|------|------|
| 껍데기 작성 (StoreTransaction, StoreTransactionRepository, TransactionCounterService/Impl) | ✅ 컴파일 성공 |
| [Red] INCR 테스트 | ✅ 실패 확인 (UnsupportedOperationException) |
| [Green] increment() 구현 | ✅ 테스트 통과 |
| [Red] flushToDB() 배치 테스트 | ✅ 실패 확인 (UnsupportedOperationException) |
| [Green] flushToDB() 구현 (GETDEL + upsert) | ✅ 2/2 테스트 통과 |
| 키 전략: store:txcount:{storeId} / String / TTL 없음 | ✅ |

구현 위치:
- StoreTransaction.java
- StoreTransactionRepository.java
- TransactionCounterServiceImpl.java
- TransactionCounterServiceTest.java

Task 2 완료 요약
TDD 흐름 (Task 1과 동일한 패턴):

단계	내용	결과
껍데기	StoreTransaction, StoreTransactionRepository, TransactionCounterService 인터페이스 + Impl (예외 던지는 stub)	✅ 컴파일 성공
[Red] INCR	increment() 3회 → Redis 값 "3" 기대	✅ UnsupportedOperationException 실패 확인
[Green] INCR	stringRedisTemplate.opsForValue().increment()	✅ 통과
[Red] Flush	flushToDB() → DB row 확인 + Redis 키 삭제 확인	✅ 실패 확인
[Green] Flush	GETDEL (원자적 읽기+삭제) → upsertCount() (ON CONFLICT 벌크)	✅ 통과
핵심 설계 포인트:

StringRedisTemplate.opsForValue().increment() → INCR 명령, 타입 안전
getAndDelete() → Redis 6.2+ GETDEL 명령으로 읽기+삭제 원자적 처리 (race condition 방지)
ON CONFLICT DO UPDATE 네이티브 쿼리로 upsert (없으면 insert, 있으면 누적)
@Scheduled(fixedDelay = 60_000) + @EnableScheduling으로 1분 주기 배치 등록

---

### 3. Null Caching — Cache Penetration 방지

**시나리오:** 존재하지 않는 계좌 반복 조회 시 매번 DB 히트 방지

- [x] [Red] 없는 계좌 조회 시 DB 1회만 호출되는 테스트 → Verify: 실패 확인
- [x] [Green] `"NULL"` 마커 캐싱으로 구현 (TTL 30초) → Verify: 테스트 통과
- [x] 정상 계좌 생성 후 Null 캐시 무효화 테스트 추가

✅ Task 3: Null Caching 완료
| 단계 | 결과 |
|------|------|
| [Red] DB 1회 호출 테스트 | ✅ 실패 확인 (TooManyActualInvocations — DB 2회 호출됨) |
| [Red] evictAccount() null 키 삭제 테스트 | ✅ 실패 확인 (AssertionFailedError — 키 미삭제) |
| [Green] Null 마커 캐싱 구현 | ✅ 2/2 테스트 통과 |

구현 포인트:
- `null:account:{id}` 키에 `"NULL"` String / TTL 30초 저장 (`StringRedisTemplate`)
- `getAccount()` 진입 시 Null 마커 먼저 확인 → 있으면 DB 미호출, 즉시 예외
- DB Miss 시 `orElseGet()` 안에서 Null 마커 저장 후 예외 throw
- `evictAccount()`에서 `account:` + `null:account:` 키 동시 삭제
- `@MockitoSpyBean` (`org.springframework.test.context.bean.override.mockito`) 사용 (Spring Boot 4 / `@SpyBean` 대체)

---

### 4. Cache Invalidation — 트랜잭션 버그 재현 및 해결

**시나리오:** 잔액 변경 후 캐시 삭제 타이밍 오류 → 낡은 데이터 노출

- [x] [Red] 트랜잭션 커밋 전 캐시 삭제 버그 테스트 → Verify: 실패 확인
  - 잔액 업데이트 중 캐시 삭제 → 다른 요청이 구버전 데이터 캐싱
- [x] 버그 재현 확인 → Verify: 테스트 통과 (버그가 있다는 증명)
- [x] [Green] `@TransactionalEventListener(AFTER_COMMIT)` 으로 수정 → Verify: 버그 테스트 실패, 정상 테스트 통과

✅ Task 4: Cache Invalidation 완료
| 단계 | 결과 |
|------|------|
| 껍데기 (AccountBalanceService/Impl) | ✅ 컴파일 성공 |
| [버그 구현] 트랜잭션 안에서 evictAccount() 직접 호출 | ✅ 버그 동작 확인 |
| [Red] 롤백 시 캐시가 삭제되는 버그 테스트 | ✅ 통과 (버그 증명) |
| [Green] AFTER_COMMIT 이벤트 리스너 적용 | ✅ 버그 테스트 실패 → 정상 테스트 통과 |

구현 포인트:
- `AccountCacheEvictEvent` (record) 발행
- `AccountCacheEvictListener` → `@TransactionalEventListener(AFTER_COMMIT)`
- 롤백 시 이벤트 리스너가 호출되지 않아 캐시 보존됨
- 커밋 성공 시에만 evictAccount() 실행 → 정합성 보장

버그 핵심 이해:
- 버그: evict가 트랜잭션 안에서 즉시 실행 → 롤백돼도 캐시는 이미 삭제
- 해결: 이벤트 발행 + AFTER_COMMIT 리스너 → 커밋 확정 후에만 캐시 삭제

---

### 5. TTL 실험 — 트레이드오프 체감

**시나리오:** TTL 길이에 따라 Hit Ratio vs 정합성이 어떻게 달라지는가

- [x] TTL 10초 / 60초 / 300초 구간별 테스트 작성
- [x] 각 TTL에서 잔액 변경 후 캐시 값 확인 → 정합성 오차 시간 측정
- [x] 결과를 주석으로 정리 (정답 없는 트레이드오프 기록)

✅ Task 5: TTL 실험 완료
| 테스트 | 결과 |
|--------|------|
| TTL 만료 전 → 캐시 Hit, 정합성 오차 체감 | ✅ |
| TTL 만료(2.5초 대기) → Cache Miss → DB 재조회 | ✅ |
| TTL + Evict 조합 → 오차 없이 최신 값 반환 | ✅ |

트레이드오프 핵심:
- TTL 짧게: 정합성↑, Hit Ratio↓, DB 부하↑
- TTL 길게: Hit Ratio↑, DB 부하↓, 정합성↓
- 최선: TTL(안전망) + 명시적 evict(즉각 반응) 조합
- `cache.account.ttl-seconds` 프로퍼티로 설정화 (`@Value` 주입)

---

### 6. Stampeding Herd 현상 재현 (Phase 2 씨앗)

**시나리오:** TTL 만료 직후 다수 요청 → DB 쿼리 폭증 현상 눈으로 확인

- [x] TTL 5초 설정 후 Testcontainers 환경에서 20 스레드 동시 조회
- [x] DB 호출 횟수 카운팅 → 1회 초과 발생 확인
- [x] 주석으로 기록: "Phase 2에서 Mutex Lock / Logical Expiration으로 해결"

✅ Task 6: Stampeding Herd 재현 완료
| 테스트 | 결과 |
|--------|------|
| TTL 만료 후 20스레드 동시 조회 → DB findById 1회 초과 확인 | ✅ |
| 캐시 살아있을 때 20스레드 조회 → DB 0회 (기준선) | ✅ |

현상 확인:
- TTL 만료 → 20스레드 동시 Cache Miss → 모두 DB 조회 → DB 쿼리 폭증
- 캐시가 살아있을 때 → 20스레드 모두 Cache Hit → DB 0회

Phase 2 해결책:
1. Mutex Lock (Redis SETNX 분산 락): 첫 스레드만 DB 조회, 나머지 대기 → 1회
2. Logical Expiration: 값에 만료시간 포함, TTL 없음 → 백그라운드 갱신

---

## Phase 1 전체 완료 요약
### 전체 테스트 현황: 전체 Green

Task	파일	테스트 수
Task 1: Cache Aside	AccountCacheServiceTest	2
Task 2: Write-Back	TransactionCounterServiceTest	2
Task 3: Null Caching	NullCachingTest	2
Task 4: Cache Invalidation	CacheInvalidationTest	2
Task 5: TTL 실험	TtlTradeoffTest	3
Task 6: Stampeding Herd	StampedingHerdTest	2

### Task 5 핵심:
cache.account.ttl-seconds 프로퍼티 설정화 (@Value 주입)
@TestPropertySource(properties = "cache.account.ttl-seconds=2")로 테스트별 TTL 오버라이드
트레이드오프: TTL 짧게(정합성↑/Hit Ratio↓) vs 길게(Hit Ratio↑/정합성↓) 주석으로 정리

### Task 6 핵심:
@MockitoSpyBean + CountDownLatch로 20스레드 동시 진입 제어
TTL 만료 후 DB findById 1회 초과 호출 → Stampeding Herd 증명
Phase 2 해결책(Mutex Lock / Logical Expiration) 주석으로 씨앗 심기


---

### 7. 통합 검증

- [x] Testcontainers 기반 전체 통합 테스트 실행 → Verify: 전체 Green
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
