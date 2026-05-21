# Phase 2 — 트러블슈팅 & 회고 (Task 0 ~ Task 5)

> Phase 2 구현 과정(Task 0~5)에서 실제로 막혔던 지점, 잘못된 접근, 설계 결정을 기록한다.
> Task 6~10은 구현 완료 후 이 문서에 이어서 추가한다.

---

## 트러블슈팅

### T-1. CacheWrapper 제네릭 record — 역직렬화 타입 소거 문제 (Task 1/3)

**발생 위치:** `LogicalExpirationCacheServiceImpl.java`

**문제:**
```java
// 저장
redisTemplate.opsForValue().set(cacheKey, new CacheWrapper<>(account, expireAt));

// 조회 시 예외 발생
org.springframework.data.redis.serializer.SerializationException: Could not read JSON:
Unexpected token (JsonToken.START_OBJECT), expected JsonToken.START_ARRAY:
need Array value to contain As.WRAPPER_ARRAY type information for class java.lang.Object
```

**원인:**
`CacheWrapper<Account>`는 제네릭 record이다.
`GenericJacksonJsonRedisSerializer` + `DefaultTyping.NON_FINAL` 조합에서 직렬화할 때 타입 정보가 `["com.example.wepay.cache.CacheWrapper", {...}]` 형태로 저장되지만, 역직렬화 시 내부 제네릭 타입 `T`가 소거(type erasure)되어 `Object`로 복원된다. 이후 `Object`를 `Account`로 캐스팅하면 `ClassCastException`이 발생한다.

**시도한 해결:**
- `redisTemplate.opsForValue().get(key)`의 반환 타입을 `CacheWrapper<?>`로 제한하고 `wrapper.data()`를 `(Account)`로 캐스팅 → 역직렬화 자체가 실패하므로 의미 없음
- Jackson `TypeReference<CacheWrapper<Account>>`를 사용하려 했으나 `GenericJacksonJsonRedisSerializer`는 `TypeReference` API를 직접 지원하지 않음

**최종 해결:** 제네릭 record를 버리고 `Account`를 고정 타입으로 갖는 구체 클래스로 전환

```java
// Before: 제네릭 record (역직렬화 실패)
public record CacheWrapper<T>(T data, long expireAt) { ... }

// After: Account 고정 구체 클래스 (역직렬화 성공)
public class AccountCacheWrapper {
    private Account data;
    private long expireAt;
    protected AccountCacheWrapper() {} // Jackson 역직렬화용 기본 생성자 필수
    ...
}
```

> **핵심 교훈:** `GenericJacksonJsonRedisSerializer`에 제네릭 타입을 저장할 때는 반드시 구체 타입이나 `@class` 필드가 정확히 기록/복원될 수 있어야 한다. 제네릭 컨테이너 클래스를 캐시 값으로 쓸 때는 구체 래퍼 클래스로 만들거나, 커스텀 직렬화를 구현해야 한다.

---

### T-2. Spring Boot 4 — `@WebMvcTest` 패키지 경로 변경 (Task 5)

**발생 위치:** `AccountControllerTest.java`

**문제:**
```java
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// → IDE 오류: The import org.springframework.boot.test.autoconfigure.web cannot be resolved
```

**원인:**
Spring Boot 4에서 스타터 모듈이 기술별로 분리됐다.
MVC 관련 테스트 어노테이션의 패키지가 변경됐다.

| 항목 | Spring Boot 3.x | Spring Boot 4.x |
|------|-----------------|-----------------|
| `@WebMvcTest` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| 의존성 | `spring-boot-starter-test` 포함 | `spring-boot-starter-webmvc-test` 별도 추가 |
| `MockMvcTester` | 없음 (3.2+부터 추가) | 기본 제공 (AssertJ 스타일) |

**해결:**
```java
// Before (Spring Boot 3.x)
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

// After (Spring Boot 4.x)
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
```

`build.gradle`에 `spring-boot-starter-webmvc-test`가 이미 추가되어 있었으나, 패키지 경로가 바뀐 것을 인지하지 못해 발생한 문제였다.

> **핵심 교훈:** Spring Boot 4 마이그레이션 시 `org.springframework.boot.test.autoconfigure.*` 하위 어노테이션은 전부 새 모듈 패키지로 이동했을 가능성이 높다. 컴파일 오류 발생 시 JAR 내 클래스 위치를 직접 확인하는 것이 빠르다.
> ```bash
> jar tf ~/.gradle/caches/.../spring-boot-webmvc-test-4.x.jar | grep WebMvcTest
> ```

---

### T-3. Testcontainers 컨텍스트 분리 시 Redisson 연결 실패 (Task 4)

**발생 위치:** `TtlJitterCacheServiceTest.java` 첫 번째 실행

**문제:**
```
Caused by: org.redisson.client.RedisConnectionException:
  Unable to connect to Redis server: localhost/127.0.0.1:49185
```

테스트 클래스에 `@TestPropertySource(properties = "cache.account.jitter-max-seconds=30")`를 추가했더니 새 ApplicationContext가 생성됐고, 이 컨텍스트에서 Redisson이 이전에 이미 종료된 Testcontainer 포트를 참조하는 문제가 발생했다.

**원인:**
Spring Test의 컨텍스트 캐시는 프로퍼티 조합이 동일한 경우에만 같은 컨텍스트를 재사용한다.
`@TestPropertySource`에 새 프로퍼티를 추가하면 별도 컨텍스트가 생성되고, Testcontainers도 새 컨테이너를 띄운다.

Redisson Spring Boot Starter는 `spring.data.redis.host`/`spring.data.redis.port`를 읽어 자동 설정되는데, 이전 컨텍스트가 종료될 때 이전 컨테이너가 먼저 내려가버리면 새 컨텍스트의 Redisson이 **이전 포트**에 잠시 접속을 시도하는 타이밍 문제가 발생했다.

**재현 조건:**
```
1. MutexLockCacheServiceTest (컨텍스트 A, 포트 49281)가 먼저 실행
2. TtlJitterCacheServiceTest (컨텍스트 B, 포트 49185) 실행
   → 컨텍스트 A 종료 직후 컨텍스트 B 시작
   → Redisson이 이미 종료된 49185에 연결 시도 → 실패
```

**해결:**
`./gradlew cleanTest`로 캐시를 비운 뒤 재실행하면 컨텍스트 A의 잔재 없이 B가 깨끗하게 시작되어 통과한다.

근본 해결책으로는 다음 두 가지를 검토할 수 있다:
1. 가능한 한 `@TestPropertySource` 조합을 통일하여 컨텍스트를 공유한다
2. Testcontainers `@Container`를 `static`으로 선언하여 JVM 생명주기 동안 단일 컨테이너를 유지한다

> **핵심 교훈:** `@TestPropertySource`가 다른 테스트 클래스는 별도 ApplicationContext를 생성한다. 컨텍스트 수가 늘어날수록 Testcontainers 컨테이너도 여러 개 띄워진다. 이 타이밍 이슈는 `cleanTest` 없이 단독 실행 시에는 재현되지 않아 디버깅이 어렵다.

---

### T-4. `@MockitoBean` 네이밍 — 같은 타입 다중 빈 구분 (Task 5)

**발생 위치:** `AccountControllerTest.java`

**문제:**
`AccountController`가 `AccountCacheService` 구현체를 4개(`baseline`, `mutexLock`, `logicalExpiration`, `ttlJitter`) `@Qualifier`로 주입받는다. `@WebMvcTest`에서 이를 Mock으로 대체할 때 어떻게 빈 이름을 지정하는지 명확하지 않았다.

**잘못된 시도:**
```java
// 타입만 지정 → 같은 타입 빈 4개 중 어느 것인지 Spring이 판단 불가 → BeanDefinitionException
@MockitoBean
AccountCacheService cacheService;
```

**해결:**
`@MockitoBean(name = "...")` 으로 빈 이름을 명시한다. `@Service("mutexLock")`의 빈 이름과 정확히 일치해야 한다.

```java
@MockitoBean(name = "accountCacheServiceImpl")  // @Primary + 기본 빈 이름
AccountCacheService baseline;

@MockitoBean(name = "mutexLock")               // @Service("mutexLock")
AccountCacheService mutexLock;

@MockitoBean(name = "logicalExpiration")
AccountCacheService logicalExpiration;

@MockitoBean(name = "ttlJitter")
AccountCacheService ttlJitter;
```

> **핵심 교훈:** `@WebMvcTest`에서 같은 타입의 빈이 여러 개일 때는 `@MockitoBean(name = "빈이름")`으로 명시한다. 빈 이름은 `@Service("이름")` 어노테이션 값 또는 클래스명의 camelCase가 된다.

---

### T-5. AccountCacheServiceImpl 빈 이름 — `@Primary` 추가 후 Qualifier 값 확인 (Task 0/5)

**발생 위치:** `AccountController.java` 생성자

**문제:**
`AccountCacheServiceImpl`에 `@Primary`를 추가했지만 `@Qualifier`로 주입 시 어떤 이름을 써야 하는지 헷갈렸다.

`@Service` 어노테이션에 이름을 명시하지 않으면 빈 이름은 클래스명의 camelCase인 `accountCacheServiceImpl`이 된다. 패키지-프라이빗 클래스여도 빈 이름 규칙은 동일하다.

```java
// AccountCacheServiceImpl.java
@Primary
@Service  // 이름 미지정 → 빈 이름: "accountCacheServiceImpl"
class AccountCacheServiceImpl implements AccountCacheService { ... }

// AccountController.java
public AccountController(
    @Qualifier("accountCacheServiceImpl") AccountCacheService baseline, // ✅
    ...
```

> **핵심 교훈:** `@Service`에 이름을 지정하지 않으면 빈 이름 = `클래스명의 첫 글자 소문자`. 접근 제어자(package-private, public)는 빈 이름에 영향을 주지 않는다.

---

### T-6. MutexLockCacheServiceTest — `clearInvocations` 타이밍 (Task 2)

**발생 위치:** `MutexLockCacheServiceTest.java`

**문제:**
```java
mutexLockCacheService.getAccount(accountId); // 최초 캐싱 (findById 1회)
TimeUnit.MILLISECONDS.sleep(2500);           // TTL 만료 대기
// 여기서 findById 카운트를 세면 이미 1이 되어있다
```

`@MockitoSpyBean`의 invocation 카운트는 테스트 메서드 시작 시 리셋되지 않는다. 따라서 TTL 만료 대기 전에 수행한 `getAccount()` 호출의 `findById` 1회가 포함되어 `dbCallCount == 1` 조건이 우연히 맞아 떨어질 수 있다.

**해결:**
TTL 만료 대기 **전**에 `clearInvocations(accountRepository)`를 호출하여 카운트를 리셋한다.

```java
mutexLockCacheService.getAccount(accountId); // warmup
clearInvocations(accountRepository);          // ← 리셋
TimeUnit.MILLISECONDS.sleep(2500);

// 이 이후의 findById 호출만 카운트
int dbCallCount = (int) mockingDetails(accountRepository)
    .getInvocations().stream()
    .filter(inv -> inv.getMethod().getName().equals("findById"))
    .count();
assertThat(dbCallCount).isEqualTo(1);
```

> **핵심 교훈:** `@MockitoSpyBean` invocation은 테스트 메서드 경계와 무관하게 누적된다. "이 시점 이후의 호출만 세고 싶다"면 반드시 `clearInvocations()`를 명시적으로 호출해야 한다.

---

## 설계 결정 기록

### D-1. CacheWrapper record → AccountCacheWrapper 구체 클래스 전환

**결정:** `CacheWrapper<T>` 제네릭 record 대신 `AccountCacheWrapper` 구체 클래스를 사용

**이유:** `GenericJacksonJsonRedisSerializer` + `DefaultTyping.NON_FINAL` 환경에서 제네릭 타입의 역직렬화가 불가능함 (T-1 참조)

**트레이드오프:**
- 장점: 역직렬화 안정성 보장, 기본 생성자를 통한 Jackson 호환성 확보
- 단점: 다른 도메인 객체(예: `Store`)에 Logical Expiration을 적용하려면 `StoreCacheWrapper`를 별도 생성해야 함 → 재사용성 낮음

**대안 (미채택):** `Jackson2ObjectMapperBuilder`에 `TypeFactory`를 통해 JavaType을 명시하는 커스텀 직렬화 구현 → 설정 복잡도가 높아 현재 요구사항에 과함

---

### D-2. LogicalExpiration warmUp 메서드 — 인터페이스 외부 노출

**결정:** `warmUp(Long id)`는 `AccountCacheService` 인터페이스가 아닌 구현 클래스에만 정의

**이유:**
- `warmUp`은 Logical Expiration 전략에만 존재하는 개념. 인터페이스에 넣으면 다른 구현체(MutexLock, TtlJitter)가 의미 없는 빈 메서드를 구현해야 함
- 테스트에서는 `@Qualifier("logicalExpiration") LogicalExpirationCacheServiceImpl`로 구체 타입 직접 주입하여 `warmUp()` 호출

**트레이드오프:** `AccountController`에서 logicalExpiration 전략을 쓸 때 warmUp 호출이 불가능 → K6 setup 함수에서 별도 warmUp API 엔드포인트로 처리

---

### D-3. TTL Jitter 테스트 — "단일 키 Stampede 해결 못함" 증명 방식

**문제:** TTL이 60~90초이므로 실제 TTL 만료를 기다릴 수 없음

**결정:** TTL 만료 대기 대신 `redisTemplate.delete()`로 캐시를 강제 삭제한 뒤 동시 조회

```java
// TTL 만료 대기 불가(60~90초) → 강제 삭제로 Cache Miss 상황 만들기
redisTemplate.delete("account:" + accountId);
```

이 접근은 "TTL 만료"가 아닌 "캐시 없음" 상황을 재현하지만, Stampede의 본질(Cache Miss → 동시 DB 조회)을 동일하게 재현한다.

---

## Spring Boot 4 호환성 체크리스트

Phase 2 구현 중 확인된 Spring Boot 4 변경 사항 정리.

| 항목 | Spring Boot 3.x | Spring Boot 4.x |
|------|-----------------|-----------------|
| `@MockBean` | `org.springframework.boot.test.mock.mockito` | `@MockitoBean` (`spring-test`) |
| `@SpyBean` | `org.springframework.boot.test.mock.mockito` | `@MockitoSpyBean` (`spring-test`) |
| `@WebMvcTest` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| MockMvc 스타일 | `MockMvcRequestBuilders` + `ResultActions` | `MockMvcTester` (AssertJ 스타일) |
| Jackson 패키지 | `com.fasterxml.jackson` | `tools.jackson` (Jackson 3.x) |

---

## 남은 과제 (Task 6~10 진행 시 추가 예정)

- [x] ZSET / HyperLogLog / Geo 직렬화 이슈 (StringRedisTemplate vs RedisTemplate 선택) → T-7 참조
- [x] StoreController `@WebMvcTest` 슬라이스 구성 방법 → T-8 참조
- [ ] K6 부하테스트 실행 결과 및 3전략 비교

---

## 트러블슈팅 (Task 6~10)

### T-7. Geo GEOSEARCH — 반경 경계 좌표의 실제 거리 오차 (Task 8)

**발생 위치:** `StoreLocationServiceTest.java`

**문제:**
계획 문서에 명시된 테스트 시나리오를 그대로 작성했더니 첫 번째 테스트가 실패했다.

```
// 계획서의 시나리오: 강남 기준 5km 검색 → 강남만 포함 (홍대·잠실은 5km 밖)
강남(127.0495, 37.5030), 홍대(126.9246, 37.5563), 잠실(127.1000, 37.5133)

// 실제 실행 결과
AssertionError: expecting ["강남점"] but was ["강남점", "잠실점"]
```

**원인:**
계획 문서의 "강남 기준 5km → 홍대·잠실 제외"라는 전제가 실제 좌표 거리와 맞지 않았다.
계산해보면 강남→잠실 직선 거리는 약 **5.3km**로 5km 반경 경계에 매우 근접한다.
Redis `GEOSEARCH`의 내부 인코딩은 WGS84 좌표를 **52비트 Geohash**로 저장하는데,
이로 인해 최대 **0.6m** 오차가 발생할 수 있다. 5.3km는 오차를 감안하면 5km 이내로 판정될 수 있다.

**잘못된 첫 시도:**
처음에는 좌표 자체가 잘못됐다고 판단하여 좌표를 수정하려 했으나,
실제 좌표는 맞고 **가정이 틀렸던 것**이었다.

**해결:**
반경을 5km → **3km**로 좁혀서 잠실(~5.3km)이 명확하게 제외되도록 수정했다.
좌표는 변경하지 않고, 테스트의 `DisplayName`에 실제 거리 주석을 추가하여 의도를 명확히 기록했다.

```java
// Before: 계획서 기준 (잠실이 경계에 걸려 불안정)
List<String> nearby = storeLocationService.findNearbyStores(127.0495, 37.5030, 5.0);

// After: 반경을 3km로 좁혀 경계 모호성 제거
@DisplayName("강남 기준 3km 반경 검색 → 강남만 포함 (홍대·잠실은 10km+ 거리)")
List<String> nearby = storeLocationService.findNearbyStores(127.0495, 37.5030, 3.0);
```

> **핵심 교훈:** Geospatial 테스트에서 "경계 근처" 좌표는 부동소수점·Geohash 오차로 인해
> 결과가 불안정하다. 테스트 좌표는 반드시 **포함/제외 여부가 명확히 갈리는 거리 차이**를 가져야 한다.
> "5km 검색에 5.3km 거리" 같은 경계 케이스는 절대 테스트 데이터로 쓰지 않는다.

---

### T-8. StoreController `@WebMvcTest` — 서비스 빈 3개의 `@MockitoBean` 선언 (Task 9)

**발생 위치:** `StoreControllerTest.java` 작성 시

**상황:**
`AccountControllerTest`는 같은 타입(`AccountCacheService`) 빈이 4개여서 `@MockitoBean(name = "...")` 네이밍이 필수였다 (T-4 참조).
`StoreControllerTest`는 타입이 각각 다른 서비스(`StoreRankingService`, `DailyActiveUserService`, `StoreLocationService`) 3개라서 타입만으로 구분 가능했다.

**결과:**
```java
// 타입이 모두 다르므로 name 지정 불필요 — 타입만으로 자동 매칭
@MockitoBean
StoreRankingService storeRankingService;

@MockitoBean
DailyActiveUserService dailyActiveUserService;

@MockitoBean
StoreLocationService storeLocationService;
```

`@WebMvcTest`는 컨트롤러 레이어만 로드하므로 서비스 구현체는 컨텍스트에 없다.
`@MockitoBean`이 없으면 `StoreController` 생성자 주입이 실패한다는 점에서,
단일 타입일 때는 `name` 없이 `@MockitoBean`만으로 충분하다는 것을 재확인했다.

> **핵심 교훈:** `@MockitoBean(name = "...")` 이 필요한 경우는 **같은 타입의 빈이 여러 개**일 때뿐이다.
> 타입이 서로 다른 빈들은 `@MockitoBean`만으로 충분하다.
> T-4와 비교하면 차이가 명확하다.

---

### T-9. `StringRedisTemplate` vs `RedisTemplate<String, Object>` — 특수 자료구조 선택 기준 (Task 6~8)

**발생 위치:** `StoreRankingServiceImpl`, `DailyActiveUserServiceImpl`, `StoreLocationServiceImpl` 설계 시

**상황:**
Phase 1에서 도메인 객체(`Account`)를 캐싱할 때는 JSON 직렬화가 필요하여 `RedisTemplate<String, Object>`를 사용했다.
Task 6~8의 ZSET, HyperLogLog, Geo 작업에서 어떤 템플릿을 써야 할지 처음에 모호했다.

**분석:**

| 자료구조 | 저장 값 | 적합한 템플릿 |
|---------|--------|------------|
| ZSET (`store:ranking`) | 가맹점 이름(String) + 점수(double) | `StringRedisTemplate` |
| HyperLogLog (`dau:date`) | userId를 String으로 변환 | `StringRedisTemplate` |
| Geo (`store:locations`) | 가맹점 이름(String) + 좌표 | `StringRedisTemplate` |

세 경우 모두 **값이 단순 문자열**이고, JSON 직렬화가 불필요하다.
`RedisTemplate<String, Object>`를 사용하면 `"starBucks"` 대신 `["java.lang.String","starBucks"]` 형태로
저장되어 다른 Redis 클라이언트나 CLI에서 읽기 어려워진다.

**결론:**
ZSET / HyperLogLog / Geo처럼 **값이 String인 특수 자료구조**는 모두 `StringRedisTemplate`을 사용한다.
도메인 객체 직렬화가 필요한 경우에만 `RedisTemplate<String, Object>`를 사용한다.

```java
// ✅ 올바른 선택 — ZSET, HLL, Geo 모두 StringRedisTemplate
@Service
public class StoreRankingServiceImpl implements StoreRankingService {
    private final StringRedisTemplate stringRedisTemplate;
    ...
}
```

> **핵심 교훈:** `StringRedisTemplate`은 `RedisTemplate<String, String>`의 특화 버전이다.
> 값이 문자열인 모든 Redis 자료구조(ZSET score member, HLL element, Geo member)에는
> `StringRedisTemplate`이 적합하다. JSON 직렬화 오버헤드가 없고 Redis CLI에서 바로 읽힌다.

---

## 설계 결정 기록 (Task 6~10 추가분)

### D-4. Geo 테스트 좌표 반경 — 계획서 5km → 실제 3km 조정

**결정:** 계획서의 "강남 기준 5km 검색" 시나리오에서 반경을 3km로 변경

**이유:** 강남→잠실 실제 거리 ~5.3km가 Redis Geohash 오차와 맞물려 5km 경계에서 결과가 불안정했다 (T-7 참조).
좌표 데이터 자체는 계획서와 동일하게 유지하고, 반경만 조정하여 테스트 의도(단거리 제외 검증)를 명확하게 유지했다.

**트레이드오프:**
- 장점: 경계 모호성 제거, 테스트 안정성 확보
- 단점: 계획서와 반경 수치가 다름 → `@DisplayName`에 실제 거리 주석을 추가하여 보완

---

### D-5. HyperLogLog `getDailyActiveUserCount()` null 처리

**결정:** `opsForHyperLogLog().size()` 반환값이 `null`일 경우 `0L` 반환

**이유:** `StringRedisTemplate.opsForHyperLogLog().size(key)`는 키가 존재하지 않을 때 `0L`을 반환하지만,
`Long` 타입이므로 이론상 `null`이 반환될 수 있다 (NPE 방어).
테스트에서 "아무도 기록하지 않으면 count == 0" 시나리오를 별도로 검증하여 이 경로를 명시적으로 커버했다.

```java
Long count = stringRedisTemplate.opsForHyperLogLog().size(todayKey());
return count != null ? count : 0L;
```
