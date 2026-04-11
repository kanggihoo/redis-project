# Phase 2 — Stampeding Herd 해결 + 특수 자료구조

## Context

Phase 1에서 Cache Aside, Write-Back, Null Caching, Cache Invalidation을 구현 완료했고,
`StampedingHerdTest`에서 TTL 만료 시 DB 쿼리 폭증 현상을 증명했다.
Phase 2에서는 이 문제를 3가지 전략으로 해결하고 K6로 비교 부하테스트를 수행하며,
핀테크 도메인에 맞는 특수 자료구조(ZSET, HyperLogLog, Geo)를 적용한다.

---

## 필요 의존성 (사용자가 직접 추가완료)

```groovy
// build.gradle — dependencies 블록에 추가
implementation 'org.redisson:redisson-spring-boot-starter:4.3.0'
```

> 기존 spring-boot-starter-data-redis로 ZSET, HyperLogLog, Geo 모두 사용 가능.
> Redisson은 Mutex Lock(RLock)에 필요.

---

## 키 네이밍 규칙 (Phase 2 추가분)

```
lock:account:{id}          → Redisson RLock 키 (Mutex Lock 전략)
store:ranking               → ZSET / 가맹점 거래 빈도 랭킹
dau:{yyyy-MM-dd}           → HyperLogLog / 일별 활성 거래자 수
store:locations             → Geospatial / 가맹점 위치
```

---

## 파일 구조 (신규 생성 파일)

```
wepay/src/main/java/com/example/wepay/
  cache/
    CacheWrapper.java                           ← Logical Expiration 래퍼
  controller/
    AccountController.java                      ← Stampeding Herd K6 테스트용
    StoreController.java                        ← 특수 자료구조 API
  service/
    MutexLockCacheServiceImpl.java              ← 전략 1: Redisson RLock
    LogicalExpirationCacheServiceImpl.java      ← 전략 2: 논리적 만료
    TtlJitterCacheServiceImpl.java              ← 전략 3: TTL 랜덤 분산
    StoreRankingService.java                    ← ZSET 인터페이스
    StoreRankingServiceImpl.java                ← ZSET 구현
    DailyActiveUserService.java                 ← HyperLogLog 인터페이스
    DailyActiveUserServiceImpl.java             ← HyperLogLog 구현
    StoreLocationService.java                   ← Geo 인터페이스
    StoreLocationServiceImpl.java               ← Geo 구현

wepay/src/test/java/com/example/wepay/
  service/
    MutexLockCacheServiceTest.java
    LogicalExpirationCacheServiceTest.java
    TtlJitterCacheServiceTest.java
    StoreRankingServiceTest.java
    DailyActiveUserServiceTest.java
    StoreLocationServiceTest.java

load-tests/
    stampeding-herd.js                          ← K6 부하테스트 스크립트
```

## 수정 파일

```
wepay/src/main/java/com/example/wepay/service/AccountCacheServiceImpl.java
  → @Primary 추가 (4개 구현체 빈 충돌 방지)

docker-compose.yml
  → k6 서비스 추가

wepay/src/test/resources/test-data.sql
  → 가맹점 Geo 좌표 시드 데이터 추가 (필요시)
```

---

## Task 순서 및 상세

### Task 0. 인프라 준비

**0-1. `AccountCacheServiceImpl`에 `@Primary` 추가**

- 4개 구현체(baseline, mutex, logical, jitter) 빈 충돌 방지
- 기존 `AccountCacheEvictListener`가 `AccountCacheService`를 주입받으므로 baseline을 기본으로 유지
- 파일: [AccountCacheServiceImpl.java](wepay/src/main/java/com/example/wepay/service/AccountCacheServiceImpl.java)

**0-2. docker-compose.yml에 K6 서비스 추가**

```yaml
k6:
  image: grafana/k6:latest
  container_name: wepay-k6
  volumes:
    - ./load-tests:/scripts
  extra_hosts:
    - "host.docker.internal:host-gateway"
  profiles:
    - loadtest
  entrypoint: ["k6", "run", "/scripts/stampeding-herd.js"]
```

- `profiles: [loadtest]`로 분리 → `docker compose --profile loadtest up k6`로 실행
- Spring Boot는 로컬에서 실행, K6는 `host.docker.internal:8080`으로 접근

---

## TDD 규칙

1. **껍데기 먼저** → 2. **실패 테스트(Red)** → 3. **최소 구현(Green)** → 4. **리팩토링**

- 구현 없이 테스트 먼저 작성 금지 (import 에러 = Red 아님)
- 각 단계마다 ./gradlew test를 실행해서 실제 테스트 결과 확인

### Task 1. CacheWrapper — Logical Expiration 기반 클래스

**TDD 흐름:**

1. **껍데기**: `CacheWrapper` record 생성. `isExpired()`는 `return false;`
   - 파일: `wepay/src/main/java/com/example/wepay/cache/CacheWrapper.java`

   ```java
   public record CacheWrapper<T>(T data, long expireAt) {
       public boolean isExpired() { return false; }
   }
   ```

   - Verify: 컴파일 성공

2. **[Red]** `CacheWrapperTest` — 과거 시간이면 `isExpired() == true`, 미래면 `false`
   - Verify: 실패 확인 (항상 false 반환)

3. **[Green]** `return System.currentTimeMillis() > expireAt;`
   - Verify: 테스트 통과

> 주의: `GenericJacksonJsonRedisSerializer` + `DefaultTyping.NON_FINAL` 설정에서
> `CacheWrapper<Account>` 직렬화/역직렬화 호환 확인 필요.
> 제네릭 타입 소거 문제 발생 시 → `AccountCacheWrapper` 구체 클래스로 전환.

---

### Task 2. MutexLockCacheServiceImpl — 전략 1: Redisson 분산 락

**핵심 로직:**

- Cache Miss → `RedissonClient.getLock("lock:account:{id}")`로 락 획득
- 락 획득 성공: DB 조회 → 캐시 저장 → 락 해제
- 락 획득 실패: 짧은 대기 후 캐시에서 재조회 (다른 스레드가 갱신했을 것)
- Double-check: 락 획득 후에도 캐시 재확인 (이미 갱신되었을 수 있음)

**TDD 흐름:**

1. **껍데기**: `@Service("mutexLock") MutexLockCacheServiceImpl implements AccountCacheService`
   - 생성자: `AccountRepository`, `RedisTemplate<String, Object>`, `StringRedisTemplate`, `RedissonClient`, `@Value ttlSeconds`
   - `getAccount()` → `throw new UnsupportedOperationException()`
   - `evictAccount()` → 빈 메서드
   - Verify: 컴파일 성공

2. **[Red]** `MutexLockCacheServiceTest` — 20스레드 동시 조회 시 DB `findById` **정확히 1회** 호출 확인
   - `@Qualifier("mutexLock")` 주입
   - Phase 1의 `StampedingHerdTest`와 동일한 패턴 (CountDownLatch + ExecutorService)
   - 단, assert는 `dbCallCount == 1` (1회만 호출)
   - Verify: 실패 확인

3. **[Green]** Mutex Lock 로직 구현

   ```
   1. 캐시 조회 → 히트면 반환
   2. lock.tryLock(5, 1, SECONDS)
   3. 락 획득 → double-check 캐시 → DB 조회 → 캐시 저장 → unlock
   4. 락 미획득 → sleep(50ms) → 캐시 재조회 → 없으면 RuntimeException
   ```

   - Verify: 테스트 통과 (DB 1회)

4. **리팩토링**: 공통 캐시 조회 로직 private 메서드 추출

---

### Task 3. LogicalExpirationCacheServiceImpl — 전략 2: 논리적 만료

**핵심 로직:**

- 물리적 TTL 없이 `CacheWrapper(data, expireAt)` 형태로 저장
- 조회 시 `isExpired()` → true면 **stale 데이터 즉시 반환** + 백그라운드 갱신 트리거
- `ConcurrentHashMap<String, Boolean>`으로 동일 키 중복 갱신 방지
- `warmUp(Long id)` 메서드로 캐시 사전 적재 필요

**TDD 흐름:**

1. **껍데기**: `@Service("logicalExpiration") LogicalExpirationCacheServiceImpl`
   - `AccountCacheService` 인터페이스 + 추가 메서드 `warmUp(Long id)`
   - 생성자: `AccountRepository`, `RedisTemplate<String, Object>`, `@Value logicalTtlSeconds`
   - `getAccount()` → `throw new UnsupportedOperationException()`
   - Verify: 컴파일 성공

2. **[Red]** `LogicalExpirationCacheServiceTest`
   - **테스트 A**: warmUp → 논리 TTL 만료 대기 → 20스레드 동시 조회 → DB 1회 + 모든 스레드 Account 반환
   - **테스트 B**: warmUp → TTL 미만료 → 조회 → DB 0회 (캐시 직접 반환)
   - Verify: 실패 확인

3. **[Green]** 구현

   ```
   getAccount:
     1. redisTemplate.get(cacheKey) → CacheWrapper cast
     2. wrapper.isExpired()이면:
        - rebuildFlags.putIfAbsent(cacheKey, true) == null일 때만 백그라운드 갱신
        - 즉시 stale data 반환
     3. 만료 안 됐으면 data 반환
     4. 캐시에 없으면(cold start) → DB 조회 후 CacheWrapper로 저장

   warmUp:
     DB 조회 → CacheWrapper(account, now + logicalTtlMs) 저장 (TTL 없음)
   ```

   - Verify: 테스트 통과

4. **리팩토링**: ExecutorService를 Spring `@Bean`으로 관리하거나 `@PreDestroy`에서 shutdown

---

### Task 4. TtlJitterCacheServiceImpl — 전략 3: TTL 랜덤 분산

**핵심 로직:**

- 기존 `AccountCacheServiceImpl`과 동일하되, TTL에 0~30초 랜덤값 추가
- **한계**: 단일 핫 키 Stampede는 해결 못 함 (여러 키의 동시 만료 방지 목적)

**TDD 흐름:**

1. **껍데기**: `@Service("ttlJitter") TtlJitterCacheServiceImpl implements AccountCacheService`
   - Verify: 컴파일 성공

2. **[Red]** `TtlJitterCacheServiceTest`
   - **테스트 A**: 같은 키를 100번 캐싱 → Redis TTL 값이 모두 동일하지 않음 (Jitter 확인)
   - **테스트 B**: 20스레드 동시 조회(단일 키) → `dbCallCount > 1` (단일 키 Stampede 해결 못함을 증명)
   - Verify: 실패 확인

3. **[Green]** 구현

   ```java
   Duration jitteredTtl = CACHE_TTL.plusSeconds(ThreadLocalRandom.current().nextLong(0, JITTER_MAX_SECONDS));
   redisTemplate.opsForValue().set(cacheKey, account, jitteredTtl);
   ```

   - Verify: 테스트 통과

4. **리팩토링**: Jitter 범위를 `@Value`로 설정화

---

### Task 5. AccountController — REST API + K6 부하테스트

**TDD 흐름:**

1. **껍데기**: `AccountController`

   ```java
   @RestController @RequestMapping("/api/accounts")
   public class AccountController {
       private final Map<String, AccountCacheService> strategies;
       // @Qualifier로 4개 구현체 주입 → Map에 저장

       @GetMapping("/{id}")
       public Account getAccount(@PathVariable Long id,
                                 @RequestParam(defaultValue = "none") String strategy) {
           return null; // 껍데기
       }
   }
   ```

   - Verify: 컴파일 성공

2. **[Red]** `AccountControllerTest` — MockMvc로 각 strategy 파라미터별 정상 응답 확인
   - Verify: 실패 확인

3. **[Green]** `strategies.get(strategy).getAccount(id)` 구현
   - Verify: 테스트 통과

4. **K6 스크립트 작성**: `load-tests/stampeding-herd.js`
   - 4개 시나리오 순차 실행 (baseline → mutex → logical → jitter)
   - 각 시나리오: 100VU, 1000 iterations
   - `startTime` 스태거링으로 시나리오 간 간섭 방지
   - logical 시나리오 전에 warmUp 호출 (setup function)

**K6 실행 방법:**

```bash
# 1. 인프라 + Spring Boot 실행
docker compose up -d
./gradlew bootRun

# 2. K6 부하테스트
docker compose --profile loadtest run k6
```

---

### Task 6. StoreRankingService — ZSET 랭킹

**TDD 흐름:**

1. **껍데기**: `StoreRankingService` 인터페이스 + `StoreRankingServiceImpl`

   ```java
   void recordTransaction(String storeName);
   List<String> getTopStores(int limit);
   ```

   - Verify: 컴파일 성공

2. **[Red]** `StoreRankingServiceTest`
   - StarBucks 5회, McDonald 3회, Subway 7회 → `getTopStores(2)` = [Subway, StarBucks]
   - Verify: 실패

3. **[Green]** `ZINCRBY` + `ZREVRANGE` 구현

   ```java
   stringRedisTemplate.opsForZSet().incrementScore("store:ranking", storeName, 1);
   stringRedisTemplate.opsForZSet().reverseRange("store:ranking", 0, limit - 1);
   ```

   - Verify: 통과

---

### Task 7. DailyActiveUserService — HyperLogLog

**TDD 흐름:**

1. **껍데기**: `DailyActiveUserService` 인터페이스 + `DailyActiveUserServiceImpl`

   ```java
   void recordActiveUser(Long userId);
   long getDailyActiveUserCount();
   ```

   - Verify: 컴파일 성공

2. **[Red]** `DailyActiveUserServiceTest`
   - userId 1, 2, 3, 1, 2 (5회 호출, 3명 유니크) → `getDailyActiveUserCount() == 3`
   - Verify: 실패

3. **[Green]** `PFADD` + `PFCOUNT` 구현

   ```java
   stringRedisTemplate.opsForHyperLogLog().add("dau:" + LocalDate.now(), userId.toString());
   stringRedisTemplate.opsForHyperLogLog().size("dau:" + LocalDate.now());
   ```

   - Verify: 통과

---

### Task 8. StoreLocationService — Geospatial

**TDD 흐름:**

1. **껍데기**: `StoreLocationService` 인터페이스 + `StoreLocationServiceImpl`

   ```java
   void addStore(String storeName, double longitude, double latitude);
   List<String> findNearbyStores(double longitude, double latitude, double radiusKm);
   ```

   - Verify: 컴파일 성공

2. **[Red]** `StoreLocationServiceTest`
   - 강남(127.0495, 37.5030), 홍대(126.9246, 37.5563), 잠실(127.1000, 37.5133) 등록
   - 강남 기준 5km 검색 → 강남만 포함 (홍대·잠실은 5km 밖)
   - 강남 기준 15km 검색 → 3곳 모두 포함
   - Verify: 실패

3. **[Green]** `GEOADD` + `GEOSEARCH` 구현

   ```java
   stringRedisTemplate.opsForGeo().add("store:locations", new Point(lng, lat), storeName);
   stringRedisTemplate.opsForGeo().search("store:locations",
       GeoReference.fromCoordinate(lng, lat),
       new Distance(radiusKm, Metrics.KILOMETERS));
   ```

   - Verify: 통과

---

### Task 9. StoreController — 특수 자료구조 REST API

```java
@RestController @RequestMapping("/api/stores")
public class StoreController {
    // POST /api/stores/transactions/{storeName}   → recordTransaction
    // GET  /api/stores/ranking?limit=10            → getTopStores
    // POST /api/stores/dau/{userId}                → recordActiveUser
    // GET  /api/stores/dau                         → getDailyActiveUserCount
    // POST /api/stores/locations                   → addStore (body)
    // GET  /api/stores/nearby?lng=&lat=&radius=    → findNearbyStores
}
```

---

### Task 10. 통합 검증

- [ ] 전체 테스트 Green: `./gradlew test`
- [ ] docker-compose + Spring Boot 기동 후 REST API 수동 확인
- [ ] K6 부하테스트 실행 → 3전략 비교 결과 확인
- [ ] 결과를 주석/문서로 기록

---

## Task 실행 순서 (의존성 기반)

```
0. 인프라 (Primary 추가, docker-compose K6)
   ↓
1. CacheWrapper (Task 3의 기반)
   ↓
2. MutexLockCacheServiceImpl    ─┐
3. LogicalExpirationCacheServiceImpl ─┼─ 병렬 가능하나 TDD 규칙상 순차 권장
4. TtlJitterCacheServiceImpl    ─┘
   ↓
5. AccountController + K6 스크립트
   ↓
6. StoreRankingService (ZSET)    ─┐
7. DailyActiveUserService (HLL)  ─┼─ 독립적, 순서 무관
8. StoreLocationService (Geo)    ─┘
   ↓
9. StoreController
   ↓
10. 통합 검증
```

---

## 주요 주의사항

1. **빈 충돌**: `AccountCacheService` 구현체 4개 → 기존 impl에 `@Primary` 필수
2. **CacheWrapper 직렬화**: Jackson `DefaultTyping.NON_FINAL` + 제네릭 record → 역직렬화 이슈 가능. 문제 시 `AccountCacheWrapper` 구체 클래스로 전환
3. **Redisson + Testcontainers**: `redisson-spring-boot-starter`가 `spring.data.redis.host/port` 자동 감지하는지 확인. 안 되면 테스트용 `RedissonConfig` 빈 필요
4. **Logical Expiration warmUp**: K6 테스트 전 warmUp 호출 필요 (setup 함수에서 처리)
5. **TTL Jitter 한계**: 단일 핫 키 Stampede 해결 불가 — 테스트에서 이 한계를 명시적으로 증명

---

## 검증 방법

```bash
# 1. 단위/통합 테스트
./gradlew test

# 2. 인프라 기동
docker compose up -d

# 3. Spring Boot 실행
./gradlew bootRun

# 4. REST API 수동 확인
curl localhost:8080/api/accounts/1?strategy=mutex
curl -X POST localhost:8080/api/stores/transactions/StarBucks
curl localhost:8080/api/stores/ranking?limit=5

# 5. K6 부하테스트
docker compose --profile loadtest run k6
```
