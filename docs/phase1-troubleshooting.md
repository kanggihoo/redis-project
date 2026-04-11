# Phase 1 — 트러블슈팅 & 회고

> Phase 1 (Cache Aside / Write-Back / Null Caching / Cache Invalidation / TTL 실험 / Stampeding Herd) 전체 진행 과정에서 발생한 문제와 설계 결정, 회고를 기록한다.

---

## 트러블슈팅

### T-1. `@Query` text block 컴파일 에러 (Task 2 — Write-Back)

**발생 위치:** `StoreTransactionRepository.java`

**문제:**
```java
@Modifying
@Query("""
        INSERT INTO store_transactions ...
        """, nativeQuery = true)
void upsertCount(...);
```
```
error: annotation values must be of the form 'name=value'
```

**원인:** Java annotation 속성에 text block(삼중 따옴표)을 사용하면 일부 컴파일러 버전에서 파싱 오류 발생. annotation 내부에서 `"""` 문법이 지원되지 않는 경우.

**해결:** 한 줄 문자열로 변경
```java
@Query(value = "INSERT INTO store_transactions (store_id, tx_count, updated_at) VALUES (:storeId, :delta, NOW()) ON CONFLICT (store_id) DO UPDATE SET tx_count = store_transactions.tx_count + :delta, updated_at = NOW()", nativeQuery = true)
```

---

### T-2. `@SpyBean` 컴파일 에러 — Spring Boot 4 API 변경 (Task 3 — Null Caching)

**발생 위치:** `NullCachingTest.java`

**문제:**
```
error: package org.springframework.boot.test.mock.mockito does not exist
```

**원인:** Spring Boot 4.x에서 `@SpyBean` / `@MockBean`이 `spring-boot-test`에서 제거됨. Bean Override API가 Spring Framework 코어(`spring-test` 모듈)로 이동.

**해결:** Spring Framework 6.2+ 신규 API로 교체
```java
// Before (Spring Boot 3.x)
import org.springframework.boot.test.mock.mockito.SpyBean;
@SpyBean AccountRepository accountRepository;

// After (Spring Boot 4.x)
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
@MockitoSpyBean AccountRepository accountRepository;
```

| 구버전 | 신버전 |
|--------|--------|
| `@MockBean` | `@MockitoBean` |
| `@SpyBean` | `@MockitoSpyBean` |

---

### T-3. `@TestConfiguration` + `@Primary` spy 방식 실패 (Task 3 — Null Caching)

**발생 위치:** `NullCachingTest.java` 첫 번째 시도

**첫 시도:**
```java
@TestConfiguration
static class SpyConfig {
    @Bean @Primary
    AccountRepository spyAccountRepository(AccountRepository real) {
        return spy(real);
    }
}
```

**문제:** `verify()` 호출 시 `NotAMockException` 발생.
`@Primary` Bean은 새로 생성된 spy를 등록하지만, `AccountCacheServiceImpl`에는 이미 `@Primary` 이전의 실제 Bean이 주입된 상태였기 때문에, 서비스 내부의 의존성까지 교체하지 못했다.

**해결:** `@MockitoSpyBean`은 스프링 컨텍스트 내 Bean 자체를 Mockito가 proxy로 wrapping하므로, `AccountCacheServiceImpl`에 주입된 참조도 spy로 교체된다.

```java
@MockitoSpyBean
AccountRepository accountRepository; // 컨텍스트 내 모든 참조가 spy로 교체
```

---

### T-4. 버그 재현 테스트 설계 어려움 (Task 4 — Cache Invalidation)

**문제:** "트랜잭션 커밋 전 캐시 삭제" 버그를 단일 스레드 테스트에서 재현하려 했으나, Spring `@Transactional`은 메서드 종료 시 커밋하므로 `save() → evict() → 커밋`이 연속 실행되어 버그 Window가 생기지 않음.

**시도 1:** `@Transactional` 테스트 메서드 + `TestTransaction.end()`로 커밋 타이밍 제어
→ 중첩 트랜잭션이 같은 컨텍스트를 공유하여 기대한 격리가 되지 않음

**시도 2:** `EntityManager.flush()` + `evictAccount()` → 커밋 전 evict 강제
→ 멀티스레드 없이는 다른 요청이 개입할 Window가 없어 버그 재현 불완전

**최종 결론:** 버그의 **진짜 모습**은 롤백 시나리오에서 명확하게 드러남

```
버그: evict → 강제 롤백(status.setRollbackOnly())
     → DB는 복구됐지만 캐시는 이미 삭제됨
```

**해결된 테스트:**
```java
assertThatThrownBy(() -> txTemplate.execute(status -> {
    accountBalanceService.updateBalance(accountId, newBalance);
    status.setRollbackOnly();
    throw new RuntimeException("강제 롤백");
}));
// 롤백 후 캐시가 삭제되어 null → 버그 증명
assertThat(redisTemplate.opsForValue().get("account:" + accountId)).isNull();
```
→ `@TransactionalEventListener(AFTER_COMMIT)` 적용 후 캐시 보존 확인으로 해결 증명.

---

### T-5. 설계 결정 — Null 마커 저장 위치 (Task 3)

**고민:** `RedisTemplate<String, Object>` vs `StringRedisTemplate` 중 어디에 Null 마커를 저장할 것인가

**결정:** `StringRedisTemplate` 선택

**이유:**
- Null 마커는 `"NULL"` 문자열 단 하나 → JSON 직렬화 불필요
- `RedisTemplate<String, Object>`로 저장하면 값이 `{"@class":"java.lang.String","value":"NULL"}` 형태로 저장되어, `"NULL".equals(value)` 비교 시 타입 불일치 발생 가능
- `StringRedisTemplate`은 순수 문자열 비교 보장

```java
// ✅ StringRedisTemplate — 단순 문자열 비교
if ("NULL".equals(stringRedisTemplate.opsForValue().get(nullKey))) {
    throw new IllegalArgumentException("Account not found: " + id);
}
```

---

### T-6. TTL 하드코딩 → 프로퍼티 설정화 (Task 5)

**문제:** `CACHE_TTL = Duration.ofSeconds(60)`으로 하드코딩되어 있어 테스트별로 TTL을 조절할 수 없었음

**해결:** `@Value`로 프로퍼티 주입 + 테스트에서 `@TestPropertySource`로 오버라이드

```java
// 구현체
AccountCacheServiceImpl(..., @Value("${cache.account.ttl-seconds:60}") long ttlSeconds) {
    this.CACHE_TTL = Duration.ofSeconds(ttlSeconds);
}
```
```java
// 테스트
@TestPropertySource(properties = "cache.account.ttl-seconds=2")
class TtlTradeoffTest { ... }
```

---

## 회고

### 잘 된 것

**1. TDD 사이클 — 껍데기 → Red → Green**
처음부터 껍데기(컴파일만 되는 상태)를 먼저 만들고, 실패 테스트를 확인한 뒤 구현하는 사이클을 일관되게 유지했다. 각 단계의 실패 원인이 명확하여 디버깅 시간이 줄었다.

**2. Testcontainers — 실 컨테이너 테스트**
Mock 대신 PostgreSQL + Redis 실 컨테이너로 테스트하여 프로덕션과의 괴리를 최소화했다. Task 4 (Cache Invalidation)에서 트랜잭션 동작이 Mock으로는 재현 불가능했던 부분이 실 DB로 자연스럽게 해결됐다.

**3. `spring-boot-testing` 스킬 활용**
Task 3에서 `@SpyBean` 에러 발생 직후 스킬을 확인하여 `@MockitoSpyBean` 전환을 빠르게 해결했다. 스킬 확인이 디버깅 시간을 단축시킴을 직접 체감했다.

**4. 이벤트 기반 캐시 무효화**
Task 4에서 `@TransactionalEventListener(AFTER_COMMIT)` 패턴은 처음에 다소 복잡하게 느껴졌지만, 이벤트(`AccountCacheEvictEvent`) → 리스너(`AccountCacheEvictListener`)의 분리로 서비스 코드가 캐시 계층에 의존하지 않게 됐다. 결합도 감소 효과가 명확했다.

---

### 아쉬운 것

**1. Task 4 버그 재현 테스트 — 3번의 재설계**
멀티스레드 타이밍 버그를 단일 스레드로 재현하려는 첫 접근이 잘못됐다. 처음부터 "버그의 본질이 롤백 시나리오에 있다"는 것을 파악했다면 훨씬 빠르게 진행할 수 있었다. 버그를 정의할 때 "어떤 상황에서 어떤 값이 잘못되는가"를 먼저 명확히 하는 것이 중요하다.

**2. `@Query` text block 실수**
annotation에 text block을 쓰면 안 된다는 것은 사소한 실수였지만, 컴파일 전에 인지할 수 있었다. Java annotation 문법 제약을 더 잘 숙지해야 한다.

**3. Task 6 Stampeding Herd — 완전한 재현의 한계**
20스레드가 동시에 Cache Miss → DB 조회 20회를 기대했지만, JPA의 1차 캐시와 커넥션 풀 크기에 따라 실제 DB 쿼리 횟수가 달라질 수 있다. 테스트에서 `isGreaterThan(1)`로 완화한 것은 현실적인 선택이었으나, 이상적으로는 더 정밀한 측정이 필요하다.

---

## 앞으로 할 것 (Phase 2 예고)

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



## Phase 1에서 재현한 Stampeding Herd 문제를 Phase 2에서 해결

### Phase 2 주요 주제

| 주제 | 설명 | 해결 목표 |
|------|------|-----------|
| **Mutex Lock** | Redis `SETNX` 기반 분산 락 | TTL 만료 시 DB 조회 1회로 제한 |
| **Logical Expiration** | 값에 만료시간 포함, TTL 없음 | 백그라운드 갱신으로 지연 없이 서빙 |
| **Read-Through** | 캐시 미스 시 자동 DB 로드 | 애플리케이션 코드 단순화 |
| **Write-Through** | 쓰기 시 DB + 캐시 동시 갱신 | 정합성 강화 |
| **분산 락 Redisson** | Redlock 알고리즘 | 다중 인스턴스 환경 정합성 |

### Phase 2 출발점 파일

```
StampedingHerdTest.java  ← Phase 1에서 재현한 버그
                           Phase 2에서 Mutex Lock으로 dbCallCount = 1 증명
```

### Phase 2 성공 기준

```
[Stampeding Herd 결과]
  스레드 수: 20
  DB 호출 수: 1  ← Phase 2 목표 (현재: N회)
```
