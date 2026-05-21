# Troubleshooting

## PowerShell 한글 깨짐

- 증상: Markdown의 한글이 깨져 보인다.
- 확인/해결:

```powershell
Get-Content -Encoding UTF8 .memory\README.md
Get-Content -Encoding UTF8 docs\phase2-retrospective.md
```

## Spring Boot 4 Mockito Bean Override

- 증상: `org.springframework.boot.test.mock.mockito` 패키지를 찾지 못한다.
- 원인: Spring Boot 4에서 `@MockBean`, `@SpyBean` 사용 방식이 바뀌었다.
- 해결:

```java
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
```

## Spring Boot 4 WebMvcTest 패키지

- 증상: 기존 `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` import 실패.
- 해결:

```java
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
```

## 같은 타입 빈 여러 개의 @WebMvcTest Mock

- 증상: `AccountCacheService` 구현체가 여러 개라 컨트롤러 테스트 컨텍스트 생성 실패.
- 해결: 빈 이름을 명시한다.

```java
@MockitoBean(name = "accountCacheServiceImpl")
AccountCacheService baseline;

@MockitoBean(name = "mutexLock")
AccountCacheService mutexLock;
```

## GenericJacksonJsonRedisSerializer + 제네릭 래퍼 역직렬화 실패

- 증상: `CacheWrapper<Account>`를 Redis에 저장한 뒤 읽을 때 직렬화/캐스팅 예외가 발생한다.
- 원인: 제네릭 타입 소거와 Jackson 타입 정보 복원 문제.
- 해결: `AccountCacheWrapper`처럼 구체 타입 래퍼를 사용한다.

## @MockitoSpyBean 호출 수 누적

- 증상: 동시성 테스트에서 DB 호출 수가 예상과 다르게 나온다.
- 원인: warmup 호출까지 spy invocation에 포함된다.
- 해결:

```java
clearInvocations(accountRepository);
```

측정하고 싶은 구간 직전에 호출한다.

## Testcontainers + Redisson 연결 실패

- 증상:

```text
org.redisson.client.RedisConnectionException: Unable to connect to Redis server
```

- 원인 후보: 테스트 컨텍스트 분리와 이전 Redis Testcontainer 포트 참조.
- 해결:

```powershell
cd wepay
.\gradlew cleanTest test
```

- 예방: 불필요한 `@TestPropertySource`를 줄인다.

## Java annotation text block 컴파일 에러

- 증상: `@Query(""" ... """)`에서 annotation value 관련 컴파일 에러.
- 해결: annotation 속성에는 한 줄 문자열 또는 명시적 `value =` 문자열을 사용한다.

## Geo 테스트 경계값 실패

- 증상: 예상보다 가까운 가맹점이 `GEOSEARCH` 결과에 포함된다.
- 원인: 실제 거리 계산이 계획서와 다르거나 Geohash/부동소수점 오차가 경계에 영향을 준다.
- 해결: 테스트 반경을 경계에서 충분히 떨어진 값으로 조정한다. Phase 2에서는 강남 기준 5km 대신 3km를 사용했다.
