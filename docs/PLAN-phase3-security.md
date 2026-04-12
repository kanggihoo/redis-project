# Phase 3 — 보안 및 트래픽 제어

## Context

Phase 2에서 Stampeding Herd 해결 + 특수 자료구조(ZSET, HyperLogLog, Geo)를 구현 완료했다.
Phase 2의 남은 문제: "송금 API에 매크로 봇이 초당 100회 호출한다. 로그아웃해도 JWT가 계속 유효하다."
Phase 3에서는 Redis를 활용한 API Rate Limiting, JWT Blacklist, Session 관리를 구현한다.

---

## 필요 의존성 (사용자가 직접 추가)

```groovy
// build.gradle — dependencies 블록에 추가
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

> `spring-boot-starter-session-data-redis`는 이미 build.gradle에 포함되어 있음.
> Spring Security는 사용하지 않음 — HandlerInterceptor로 구현.

---

## 키 네이밍 규칙 (Phase 3 추가분)

```
ratelimit:fixed:{api}:{identifier}         → String / TTL 60s (Fixed Window 카운터)
ratelimit:sliding:{api}:{identifier}        → ZSET / TTL 60s (Sliding Window 타임스탬프)
blacklist:jwt:{jti}                         → String "1" / TTL = JWT 잔여 유효시간
```

---

## 파일 구조 (신규 생성 파일)

```
wepay/src/main/java/com/example/wepay/
  config/
    WebMvcConfig.java                        ← Interceptor 등록
  controller/
    TransferController.java                  ← Mock 송금 API (Rate Limiting 테스트용)
    AuthController.java                      ← 로그인/로그아웃 (JWT 발급/Blacklist)
  interceptor/
    RateLimitInterceptor.java                ← Rate Limiting HandlerInterceptor
    JwtBlacklistInterceptor.java             ← JWT Blacklist 체크 HandlerInterceptor
  service/
    RateLimitService.java                    ← 인터페이스
    FixedWindowRateLimitService.java         ← 전략 1: Fixed Window
    SlidingWindowRateLimitService.java       ← 전략 2: Sliding Window
    JwtTokenService.java                     ← JWT 발급/파싱/Blacklist 유틸
    SessionDemoService.java                  ← Spring Session Redis 데모

wepay/src/test/java/com/example/wepay/
  service/
    FixedWindowRateLimitServiceTest.java
    SlidingWindowRateLimitServiceTest.java
    FixedWindowBoundaryBugTest.java          ← Fixed Window 경계 조건 버그 증명
    JwtTokenServiceTest.java
    SessionDemoServiceTest.java
```

## 수정 파일

```
wepay/src/main/resources/application.yml
  → rate-limit, jwt 설정 추가

wepay/src/test/resources/test-data.sql
  → 변경 없음 (기존 Alice 계좌 그대로 사용)
```

---

## TDD 규칙

1. **껍데기 먼저** → 2. **실패 테스트(Red)** → 3. **최소 구현(Green)** → 4. **리팩토링**

- 구현 없이 테스트 먼저 작성 금지 (import 에러 = Red 아님)
- 각 단계마다 ./gradlew test를 실행해서 실제 테스트 결과 확인

---

## application.yml 추가 설정

```yaml
# Phase 3 추가
rate-limit:
  transfer:
    max-requests: 5
    window-seconds: 60
  account-read:
    max-requests: 100
    window-seconds: 60

jwt:
  secret: wepay-phase3-test-secret-key-minimum-256-bits-long-enough
  expiration-seconds: 3600
```

---

## Task 순서 및 상세

### Task 0. 인프라 준비

**0-1. application.yml에 Phase 3 설정 추가**

- `rate-limit.transfer.max-requests`, `rate-limit.transfer.window-seconds`
- `rate-limit.account-read.max-requests`, `rate-limit.account-read.window-seconds`
- `jwt.secret`, `jwt.expiration-seconds`
- Verify: `./gradlew bootRun` 정상 기동

---

### Task 1. FixedWindowRateLimitService — Fixed Window 카운터

**시나리오:** API 호출 시 Redis `INCR` + `EXPIRE`로 윈도우 내 호출 횟수 제한

**TDD 흐름:**

1. **껍데기**: `RateLimitService` 인터페이스 + `FixedWindowRateLimitService` 작성

   ```java
   public interface RateLimitService {
       boolean isAllowed(String apiKey, String identifier);
   }
   ```

   ```java
   @Service("fixedWindow")
   public class FixedWindowRateLimitService implements RateLimitService {
       public FixedWindowRateLimitService(StringRedisTemplate stringRedisTemplate,
                                           @Value("${rate-limit.transfer.max-requests}") int maxRequests,
                                           @Value("${rate-limit.transfer.window-seconds}") long windowSeconds) {
           // 필드 할당
       }

       @Override
       public boolean isAllowed(String apiKey, String identifier) {
           throw new UnsupportedOperationException();
       }
   }
   ```

   - Verify: 컴파일 성공

2. **[Red]** `FixedWindowRateLimitServiceTest`
   - **테스트 A**: 5회 호출 → 모두 true, 6번째 호출 → false
   - Verify: 실패 확인 (UnsupportedOperationException)

3. **[Green]** 구현

   ```
   isAllowed:
     1. key = "ratelimit:fixed:{apiKey}:{identifier}"
     2. count = stringRedisTemplate.opsForValue().increment(key)
     3. count == 1이면 → EXPIRE(key, windowSeconds)
     4. count > maxRequests → return false
     5. else → return true
   ```

   - Verify: 테스트 통과

4. **리팩토링**: 키 생성 로직 private 메서드 추출

---

### Task 2. FixedWindowBoundaryBugTest — Fixed Window 경계 조건 버그 증명

**시나리오:** README의 핵심 — "59초에 5회 + 61초에 5회 = 2초간 10회 통과" 버그 재현

**TDD 흐름:**

1. **[Red → 버그 증명]** `FixedWindowBoundaryBugTest`
   - TTL 2초로 설정 (테스트용 빠른 윈도우)
   - 1초 시점에 maxRequests회 호출 → 모두 허용
   - 2.5초 대기 (윈도우 만료)
   - 즉시 maxRequests회 호출 → 모두 허용
   - **결과: 실질적으로 짧은 시간 내 2 × maxRequests 통과됨**
   - 이것이 Fixed Window의 한계임을 주석으로 기록
   - Verify: 테스트 통과 (버그가 있다는 증명)

> 이 테스트는 "통과하면 버그가 있다"는 것을 증명하는 테스트.
> Phase 1의 Cache Invalidation 버그 재현 패턴과 동일한 접근.

---

### Task 3. SlidingWindowRateLimitService — Sliding Window (ZSET)

**시나리오:** ZSET에 타임스탬프를 score로 저장, 윈도우 범위 내 요소 수로 판단

**TDD 흐름:**

1. **껍데기**: `SlidingWindowRateLimitService` 작성

   ```java
   @Service("slidingWindow")
   public class SlidingWindowRateLimitService implements RateLimitService {
       public SlidingWindowRateLimitService(StringRedisTemplate stringRedisTemplate,
                                             @Value("${rate-limit.transfer.max-requests}") int maxRequests,
                                             @Value("${rate-limit.transfer.window-seconds}") long windowSeconds) {
           // 필드 할당
       }

       @Override
       public boolean isAllowed(String apiKey, String identifier) {
           throw new UnsupportedOperationException();
       }
   }
   ```

   - Verify: 컴파일 성공

2. **[Red]** `SlidingWindowRateLimitServiceTest`
   - **테스트 A**: 5회 호출 → 모두 true, 6번째 → false
   - **테스트 B (경계 조건 해결 증명)**: TTL 2초, maxRequests=3
     - 3회 호출 → 허용
     - 1초 대기 (윈도우 만료 전)
     - 4번째 호출 → **차단** (Fixed Window와 다른 결과!)
   - Verify: 실패 확인

3. **[Green]** 구현

   ```
   isAllowed:
     1. key = "ratelimit:sliding:{apiKey}:{identifier}"
     2. now = System.currentTimeMillis()
     3. windowStart = now - (windowSeconds * 1000)
     4. ZREMRANGEBYSCORE(key, 0, windowStart)  ← 윈도우 밖 제거
     5. count = ZCARD(key)
     6. count >= maxRequests → return false
     7. ZADD(key, now, now + ":" + UUID)  ← 고유 member
     8. EXPIRE(key, windowSeconds)  ← 키 자체 TTL (메모리 안전망)
     9. return true
   ```

   - Verify: 테스트 통과

4. **리팩토링**: ZREMRANGEBYSCORE + ZCARD + ZADD를 하나의 흐름으로 정리

---

### Task 4. RateLimitInterceptor + WebMvcConfig — HTTP 적용

**시나리오:** HandlerInterceptor로 실제 HTTP 요청에 Rate Limiting 적용

**TDD 흐름:**

1. **껍데기**: `RateLimitInterceptor`, `WebMvcConfig` 작성

   ```java
   @Component
   public class RateLimitInterceptor implements HandlerInterceptor {
       public RateLimitInterceptor(
               @Qualifier("slidingWindow") RateLimitService rateLimitService) {
           // 필드 할당
       }

       @Override
       public boolean preHandle(HttpServletRequest request,
                                 HttpServletResponse response,
                                 Object handler) throws Exception {
           return true; // 껍데기: 모두 통과
       }
   }
   ```

   ```java
   @Configuration
   public class WebMvcConfig implements WebMvcConfigurer {
       @Override
       public void addInterceptors(InterceptorRegistry registry) {
           registry.addInterceptor(rateLimitInterceptor)
                   .addPathPatterns("/api/transfer/**", "/api/accounts/**");
       }
   }
   ```

   - Verify: 컴파일 성공

2. **껍데기**: `TransferController` — Mock 송금 엔드포인트

   ```java
   @RestController
   @RequestMapping("/api/transfer")
   public class TransferController {

       @PostMapping
       public Map<String, Object> transfer(@RequestBody Map<String, Object> request) {
           return Map.of("status", "SUCCESS",
                         "message", "송금 완료 (Mock)",
                         "amount", request.getOrDefault("amount", 0));
       }
   }
   ```

   - Verify: 컴파일 성공

3. **[Red]** `RateLimitInterceptorTest` (@SpringBootTest + Testcontainers)
   - **테스트 A**: 송금 API 5회 호출 → 200 OK, 6번째 → 429 Too Many Requests
   - **테스트 B**: 잔액 조회 API 100회 호출 → 모두 200 OK (느슨한 제한)
   - Verify: 실패 확인 (껍데기가 모두 통과시키므로 429 안 나옴)

4. **[Green]** `RateLimitInterceptor.preHandle()` 구현

   ```
   preHandle:
     1. identifier = request.getRemoteAddr() (IP 기반)
     2. apiKey = request URI에서 추출 ("/api/transfer" or "/api/accounts")
     3. rateLimitService.isAllowed(apiKey, identifier)
     4. false → response.setStatus(429) + JSON 에러 바디 + return false
     5. true → return true
   ```

   - Verify: 테스트 통과

5. **리팩토링**: API별 maxRequests/windowSeconds 분리 적용
   - `/api/transfer` → 1분 5회 (엄격)
   - `/api/accounts` → 1분 100회 (느슨)

---

### Task 5. JwtTokenService — JWT 발급 + Blacklist

**시나리오:** 테스트용 JWT 발급/파싱 + Redis Blacklist로 무효화

**TDD 흐름:**

1. **껍데기**: `JwtTokenService` 작성

   ```java
   @Service
   public class JwtTokenService {
       public JwtTokenService(StringRedisTemplate stringRedisTemplate,
                               @Value("${jwt.secret}") String secret,
                               @Value("${jwt.expiration-seconds}") long expirationSeconds) {
           // 필드 할당
       }

       /** 테스트용 JWT 토큰 생성 */
       public String generateToken(String userId) {
           throw new UnsupportedOperationException();
       }

       /** JWT에서 userId 추출 */
       public String parseUserId(String token) {
           throw new UnsupportedOperationException();
       }

       /** JWT에서 jti(토큰 고유 ID) 추출 */
       public String parseJti(String token) {
           throw new UnsupportedOperationException();
       }

       /** 로그아웃 — 잔여 유효시간만큼 Redis에 Blacklist 등록 */
       public void blacklist(String token) {
           throw new UnsupportedOperationException();
       }

       /** 토큰이 Blacklist에 있는지 확인 */
       public boolean isBlacklisted(String token) {
           throw new UnsupportedOperationException();
       }
   }
   ```

   - Verify: 컴파일 성공

2. **[Red]** `JwtTokenServiceTest`
   - **테스트 A (발급 + 파싱)**: generateToken("user1") → parseUserId() == "user1"
   - **테스트 B (Blacklist 등록)**: generateToken → blacklist → isBlacklisted == true
   - **테스트 C (Blacklist 미등록 토큰)**: generateToken → isBlacklisted == false
   - **테스트 D (TTL 검증)**: generateToken(짧은 만료) → blacklist → Redis TTL이 잔여 유효시간 이하인지 확인
   - Verify: 실패 확인

3. **[Green]** 구현

   ```
   generateToken:
     1. jti = UUID.randomUUID()
     2. Jwts.builder()
          .id(jti)
          .subject(userId)
          .issuedAt(now)
          .expiration(now + expirationSeconds)
          .signWith(secretKey)
          .compact()

   parseUserId:
     1. Jwts.parser().verifyWith(secretKey).build()
          .parseSignedClaims(token).getPayload().getSubject()

   parseJti:
     1. 동일 파싱 → .getId()

   blacklist:
     1. jti = parseJti(token)
     2. expiration = parseExpiration(token)
     3. remainingSeconds = (expiration - now) / 1000
     4. remainingSeconds > 0이면:
        stringRedisTemplate.opsForValue()
          .set("blacklist:jwt:" + jti, "1", remainingSeconds, TimeUnit.SECONDS)

   isBlacklisted:
     1. jti = parseJti(token)
     2. return stringRedisTemplate.hasKey("blacklist:jwt:" + jti)
   ```

   - Verify: 테스트 통과

---

### Task 6. JwtBlacklistInterceptor + AuthController — HTTP 적용

**시나리오:** 로그아웃 후 동일 JWT로 API 호출 시 즉시 거부

**TDD 흐름:**

1. **껍데기**: `JwtBlacklistInterceptor` 작성

   ```java
   @Component
   public class JwtBlacklistInterceptor implements HandlerInterceptor {
       public JwtBlacklistInterceptor(JwtTokenService jwtTokenService) {
           // 필드 할당
       }

       @Override
       public boolean preHandle(HttpServletRequest request,
                                 HttpServletResponse response,
                                 Object handler) throws Exception {
           return true; // 껍데기: 모두 통과
       }
   }
   ```

   - Verify: 컴파일 성공

2. **껍데기**: `AuthController` — 로그인/로그아웃

   ```java
   @RestController
   @RequestMapping("/api/auth")
   public class AuthController {
       private final JwtTokenService jwtTokenService;

       @PostMapping("/login")
       public Map<String, String> login(@RequestBody Map<String, String> request) {
           return Map.of("token", "dummy"); // 껍데기
       }

       @PostMapping("/logout")
       public Map<String, String> logout(@RequestHeader("Authorization") String authHeader) {
           return Map.of("status", "dummy"); // 껍데기
       }
   }
   ```

   - Verify: 컴파일 성공

3. **WebMvcConfig 수정**: JwtBlacklistInterceptor 등록
   - `/api/auth/login` 제외, 나머지 `/api/**` 경로에 적용
   - `Authorization` 헤더가 없으면 통과 (선택적 인증)

4. **[Red]** `JwtBlacklistInterceptorTest` (@SpringBootTest + Testcontainers)
   - **테스트 A**: 로그인 → JWT 받기 → 해당 JWT로 API 호출 → 200 OK
   - **테스트 B**: 로그인 → JWT 받기 → 로그아웃 → 동일 JWT로 API 호출 → **401 Unauthorized**
   - **테스트 C**: Authorization 헤더 없이 API 호출 → 200 OK (선택적 인증)
   - Verify: 실패 확인

5. **[Green]** 구현

   ```
   AuthController.login:
     1. userId = request.get("userId")
     2. token = jwtTokenService.generateToken(userId)
     3. return Map.of("token", token)

   AuthController.logout:
     1. token = authHeader에서 "Bearer " 제거
     2. jwtTokenService.blacklist(token)
     3. return Map.of("status", "LOGGED_OUT")

   JwtBlacklistInterceptor.preHandle:
     1. authHeader = request.getHeader("Authorization")
     2. authHeader가 null이거나 "Bearer "로 시작 안 하면 → return true (통과)
     3. token = "Bearer " 이후 문자열
     4. jwtTokenService.isBlacklisted(token) → true면:
        response.setStatus(401) + JSON 에러 바디 + return false
     5. return true
   ```

   - Verify: 테스트 통과

---

### Task 7. SessionDemoService — Spring Session Redis

**시나리오:** Spring Session Redis 설정 확인 + 세션 저장/조회 테스트

**TDD 흐름:**

1. **껍데기**: `SessionDemoService` 작성

   ```java
   @Service
   public class SessionDemoService {
       /** 세션에 값 저장 */
       public void setAttribute(HttpSession session, String key, String value) {
           throw new UnsupportedOperationException();
       }

       /** 세션에서 값 조회 */
       public String getAttribute(HttpSession session, String key) {
           throw new UnsupportedOperationException();
       }
   }
   ```

   - Verify: 컴파일 성공

2. **[Red]** `SessionDemoServiceTest` (@SpringBootTest + Testcontainers)
   - **테스트 A**: MockMvc로 세션에 값 저장 → 동일 세션으로 조회 → 값 일치
   - **테스트 B**: Redis에 `spring:session:*` 키가 생성되었는지 확인
   - Verify: 실패 확인

3. **[Green]** 구현

   ```java
   public void setAttribute(HttpSession session, String key, String value) {
       session.setAttribute(key, value);
   }

   public String getAttribute(HttpSession session, String key) {
       Object value = session.getAttribute(key);
       return value != null ? value.toString() : null;
   }
   ```

   - Verify: 테스트 통과

4. **SessionController** (세션 테스트용 간단 엔드포인트)

   ```java
   @RestController
   @RequestMapping("/api/session")
   public class SessionController {
       @PostMapping("/{key}")
       public Map<String, String> set(HttpSession session,
                                       @PathVariable String key,
                                       @RequestBody Map<String, String> body) {
           session.setAttribute(key, body.get("value"));
           return Map.of("sessionId", session.getId());
       }

       @GetMapping("/{key}")
       public Map<String, String> get(HttpSession session, @PathVariable String key) {
           Object value = session.getAttribute(key);
           return Map.of("value", value != null ? value.toString() : "");
       }
   }
   ```

---

### Task 8. 통합 검증

- [ ] 전체 테스트 Green: `./gradlew test`
- [ ] docker-compose + Spring Boot 기동 후 REST API 수동 확인
- [ ] 결과를 주석/문서로 기록

---

## Task 실행 순서 (의존성 기반)

```
0. 인프라 (application.yml 설정 추가)
   ↓
1. FixedWindowRateLimitService      ─┐
2. FixedWindowBoundaryBugTest        ├─ Rate Limiting 핵심
3. SlidingWindowRateLimitService    ─┘
   ↓
4. RateLimitInterceptor + WebMvcConfig + TransferController (HTTP 적용)
   ↓
5. JwtTokenService (JWT 발급/Blacklist 핵심)
   ↓
6. JwtBlacklistInterceptor + AuthController (HTTP 적용)
   ↓
7. SessionDemoService + SessionController (Spring Session Redis)
   ↓
8. 통합 검증
```

---

## 주요 주의사항

1. **Spring Boot 4 테스트 어노테이션**: `@MockitoBean` / `@MockitoSpyBean` 사용 (`@MockBean` 사용 금지)
2. **HandlerInterceptor 순서**: Rate Limiting → JWT Blacklist 순서로 등록 (Rate Limit 먼저 체크해서 불필요한 JWT 파싱 방지)
3. **WebMvcConfig에서 /api/auth/login 제외**: 로그인 엔드포인트에는 JWT 체크 불필요, Rate Limiting만 적용
4. **JWT secret 최소 길이**: HMAC-SHA256 기준 256비트(32바이트) 이상
5. **Sliding Window ZSET 멤버 고유성**: `timestamp:UUID`로 같은 밀리초 내 중복 방지
6. **빈 충돌**: `RateLimitService` 구현체 2개 → `@Qualifier`로 구분, Interceptor에서는 `slidingWindow` 사용
7. **테스트 격리**: 각 테스트 `@BeforeEach`에서 `ratelimit:*`, `blacklist:*` Redis 키 정리

---

## 검증 방법

```bash
# 1. 단위/통합 테스트
./gradlew test

# 2. 인프라 기동
docker compose up -d

# 3. Spring Boot 실행
./gradlew bootRun

# 4. Rate Limiting 수동 확인
for i in {1..6}; do curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/api/transfer -H "Content-Type: application/json" -d '{"amount":10000}'; done
# 예상: 200 200 200 200 200 429

# 5. JWT Blacklist 수동 확인
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"userId":"user1"}' | jq -r '.token')
curl -s localhost:8080/api/accounts/1 -H "Authorization: Bearer $TOKEN"  # → 200
curl -s -X POST localhost:8080/api/auth/logout -H "Authorization: Bearer $TOKEN"  # → 로그아웃
curl -s localhost:8080/api/accounts/1 -H "Authorization: Bearer $TOKEN"  # → 401

# 6. Session 수동 확인
curl -s -X POST localhost:8080/api/session/username -H "Content-Type: application/json" -d '{"value":"Alice"}' -c cookie.txt
curl -s localhost:8080/api/session/username -b cookie.txt  # → {"value":"Alice"}
```

---

## Phase 3에서 얻는 인사이트

- Fixed Window의 경계 조건 버그가 왜 위험한가 (2초간 2배 트래픽 통과)
- Sliding Window가 더 정밀하지만 메모리를 더 사용하는 트레이드오프 (ZSET vs 단순 카운터)
- JWT Blacklist의 TTL = 토큰 잔여 시간으로 메모리 자동 정리 패턴
- Spring Session Redis의 동작 원리 확인

## ❓ 남은 문제 → Phase 4로

> "보안은 잡았는데, 동시에 같은 계좌에서 송금하면 잔액이 마이너스가 된다."
