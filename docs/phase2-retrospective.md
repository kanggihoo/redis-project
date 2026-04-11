# Phase 2 — 회고 및 Phase 3 개선 계획

> Phase 2(Stampeding Herd 해결 + 특수 자료구조) 구현을 마친 뒤 배운 점을 정리하고,
> 이 경험을 바탕으로 Phase 3(보안 및 트래픽 제어)에서 무엇을 다르게 할지 기록한다.

---

## 1. Phase 2 회고

### 1-1. 잘 된 것

#### TDD 흐름이 실제로 문제를 일찍 발견해줬다

껍데기 → Red → Green 순서를 지켰을 때 가장 큰 이점은
"내가 틀린 가정을 코드를 다 짜기 전에 알 수 있다"는 점이었다.

Geo 테스트(T-7)에서 계획서의 "강남 기준 5km" 시나리오가 실제 좌표와 맞지 않는다는 사실을
구현을 완성하기 전 Red 단계에서 바로 알 수 있었다.
만약 구현을 먼저 짜고 나서 테스트를 작성했다면 "왜 맞는 것 같은데 실패하지?"를 훨씬 오래 붙잡고 있었을 것이다.

#### 특수 자료구조(ZSET / HyperLogLog / Geo) 선택 기준이 명확해졌다

처음에는 "객체도 아니고 단순 값인데 굳이 `RedisTemplate<String, Object>`와 `StringRedisTemplate` 중 뭘 써야 하지?" 라는 감이 없었다.
Task 6~8을 직접 구현하면서 기준이 명확해졌다.

- 값이 도메인 객체(Account 등): `RedisTemplate<String, Object>` → JSON 직렬화 필요
- 값이 단순 문자열(가맹점 이름, userId 등): `StringRedisTemplate` → 직렬화 오버헤드 없음

이 기준은 Phase 3의 Rate Limiting(카운터), JWT Blacklist(토큰 문자열)에도 그대로 적용된다.

#### `@WebMvcTest` 슬라이스 테스트의 활용법을 체화했다

Phase 1에서는 서비스 테스트를 모두 `@SpringBootTest`로 작성했다.
Phase 2에서 `AccountControllerTest`, `StoreControllerTest`를 `@WebMvcTest`로 작성하면서
컨트롤러 레이어는 웹 레이어만 로드하고 서비스를 Mock으로 분리하는 것이 얼마나 빠른지 체감했다.
`@SpringBootTest`가 25~30초 걸릴 때 `@WebMvcTest`는 2초 안에 끝났다.

---

### 1-2. 아쉬웠던 것

#### 계획서의 좌표 데이터를 검증 없이 신뢰했다

T-7에서 발생한 Geo 테스트 실패의 근본 원인은 계획서에 적힌 좌표/반경 조합을
실제 거리 계산 없이 그대로 테스트에 옮겼다는 점이다.
지도 API나 간단한 하버사인 공식으로 미리 검산했다면 방지할 수 있었다.

**개선 방향:** 지리 좌표가 포함된 테스트 데이터는 작성 전에 반드시 실제 거리를 계산한다.
경계 조건(포함 여부가 불분명한 거리)은 테스트 데이터로 사용하지 않는다.

#### Stampeding Herd 3전략의 K6 부하테스트 실행을 Phase 2 내에서 완료하지 못했다

계획서(Task 5)에 K6 스크립트 작성이 포함되어 있었고 `docker-compose.yml`에도 k6 서비스가 추가됐지만,
단위/통합 테스트로 각 전략의 정확성을 검증하는 데 집중하다 보니
3전략을 직접 비교하는 부하테스트 실행과 수치 기록은 이루어지지 않았다.

**개선 방향:** Phase 3 완료 시점에 Phase 2의 K6 비교 시나리오를 함께 실행하여
"Mutex Lock vs Logical Expiration vs TTL Jitter" DB 쿼리 수를 실제 수치로 기록한다.

#### `@TestPropertySource`가 다른 테스트끼리의 컨텍스트 공유 비용을 사전에 고려하지 못했다

T-3(Redisson 연결 실패 타이밍 이슈)는 Phase 2에서 이미 경험한 문제임에도,
Task 6~8 테스트를 설계할 때 컨텍스트 분리 비용을 처음부터 고려하진 못했다.
(다행히 Task 6~8은 `@TestPropertySource`를 추가하지 않아 동일 컨텍스트를 재사용했다)

**개선 방향:** 새 테스트 클래스를 만들 때 `@TestPropertySource` 추가가 필요한지 먼저 확인한다.
필요 없으면 생략하여 컨텍스트 수를 최소화한다.

---

### 1-3. 배운 핵심 원칙

| # | 원칙 | 근거 |
|---|------|------|
| 1 | **경계값 테스트 데이터는 명확한 차이를 만들어야 한다** | Geo 5km 경계 이슈(T-7) |
| 2 | **특수 자료구조 값이 String이면 StringRedisTemplate을 써라** | ZSET/HLL/Geo 설계(T-9) |
| 3 | **컨트롤러 테스트는 @WebMvcTest로, 서비스 통합 테스트는 @SpringBootTest로** | 테스트 속도 체감 |
| 4 | **@MockitoBean(name=) 은 같은 타입이 여러 개일 때만 필요하다** | T-4 vs T-8 비교 |
| 5 | **@TestPropertySource가 다르면 컨텍스트가 달라져 Testcontainers 타이밍 이슈 발생** | T-3 |
| 6 | **GenericJacksonJsonRedisSerializer + 제네릭 타입 = 역직렬화 실패** | T-1, CacheWrapper → AccountCacheWrapper |

---

## 2. Phase 3에서 개선할 것

> Phase 3 목표: **보안 및 트래픽 제어** — Rate Limiting, JWT Blacklist, Session Management

### 2-1. Phase 2의 문제를 Phase 3에서 반복하지 않기 위한 규칙

#### 규칙 1. 테스트 데이터 경계값 사전 검증

Phase 2 Geo 이슈(T-7)의 재발 방지.

Rate Limiting 테스트에서 "1분 5회 제한"을 검증할 때,
`Fixed Window`의 경계 조건(59초에 5회 + 61초에 5회)은 시간 의존적 경계값이다.
슬리핑 타이밍이 플랫폼마다 달라 불안정할 수 있으므로,
**실제 시간 진행 대신 `Clock` 추상화(테스트용 가짜 시계)를 주입**하는 설계를 우선 검토한다.

#### 규칙 2. 컨텍스트 수 최소화 전략 유지

Phase 2에서 확립한 원칙: `@TestPropertySource`가 다르면 새 컨텍스트 = 새 Testcontainer.

Phase 3에서 Rate Limiting, JWT Blacklist, Session 테스트를 각각 별도 클래스로 만들 때,
공통 프로퍼티는 `application-test.yml`에 정의하고 클래스별 오버라이드를 최소화한다.

```yaml
# src/test/resources/application-test.yml (공통 기반)
rate-limit:
  transfer:
    max-requests: 5
    window-seconds: 60
  balance:
    max-requests: 100
    window-seconds: 60
```

#### 규칙 3. StringRedisTemplate 우선 사용

Phase 2에서 확립한 원칙: 값이 String인 자료구조 → `StringRedisTemplate`.

Phase 3의 Redis 사용 패턴 예측:

| 기능 | Redis 명령 | 값 타입 | 사용할 템플릿 |
|------|-----------|--------|------------|
| Rate Limiting (Fixed Window) | `INCR`, `EXPIRE` | 숫자(카운터) | `StringRedisTemplate` |
| Rate Limiting (Sliding Window) | `ZADD`, `ZCOUNT`, `ZREMRANGEBYSCORE` | timestamp(String) | `StringRedisTemplate` |
| JWT Blacklist | `SET key 1 EX <ttl>` | 문자열 플래그 | `StringRedisTemplate` |
| Session | Spring Session Redis 자동 관리 | 객체 직렬화 | 프레임워크 위임 |

→ Phase 3에서도 `RedisTemplate<String, Object>`가 필요한 경우는 거의 없다.

#### 규칙 4. Sliding Window 테스트 — 시간 의존성 격리

README의 Phase 3 시나리오:
> "Fixed Window 경계 시점에 60회 연속 호출 → 59초에 5회 + 61초에 5회 = 2초간 10회 통과 (버그)"

이 테스트는 실제 `Thread.sleep()`에 의존하면 CI 환경에서 불안정하다.
`Clock`을 인터페이스로 추상화하고 테스트에서 `FakeClock`을 주입하는 설계를 사용한다.

```java
// 프로덕션
@Bean
Clock clock() { return Clock.systemUTC(); }

// 테스트
Clock fakeClock = mock(Clock.class);
given(fakeClock.millis()).willReturn(59_000L, 59_001L, 59_002L, 61_000L, ...);
```

이 패턴을 Phase 2에서 미리 도입하지 못한 아쉬움을 Phase 3에서 보완한다.

#### 규칙 5. JWT TTL 계산 — 도메인 로직은 단위 테스트로 분리

JWT Blacklist의 핵심 로직은 "로그아웃 시점부터 토큰 만료까지 남은 시간을 TTL로 설정"이다.
이 계산 로직은 Redis와 무관한 순수 도메인 로직이므로,
Redis 연동 없이 **순수 단위 테스트(JUnit + Mockito)** 로 검증한다.

```
테스트 피라미드 적용:
- TTL 계산 로직: 단위 테스트 (Redis 없음)
- Blacklist 저장/조회: @SpringBootTest + Testcontainers (Redis 있음)
- Rate Limiting 엔드포인트: @WebMvcTest (서비스 Mock)
```

Phase 2에서 Stampeding Herd 서비스 테스트를 모두 `@SpringBootTest`로 작성한 것과 달리,
Phase 3에서는 **테스트 피라미드를 더 의식적으로** 적용한다.

---

### 2-2. Phase 3에서 새로 도전할 것

#### Fixed Window vs Sliding Window 버그를 코드로 직접 증명

README의 Phase 3 시나리오 그대로:
Fixed Window의 경계 조건 버그를 테스트로 재현하여 "버그가 있는 구현 → 테스트 통과 → Sliding Window로 교체 → 테스트 통과" 흐름을 문서화한다.

Phase 2에서 `TtlJitterCacheServiceTest`의 "[TTL Jitter B] 단일 키 Stampede 해결 못함을 증명"처럼,
**한계를 테스트로 증명**하는 패턴을 Rate Limiting에도 적용한다.

#### Spring Security 없이 JWT Blacklist 구현

Phase 3의 JWT Blacklist는 Spring Security 의존 없이 `OncePerRequestFilter` + Redis 조회만으로 구현한다.
이유: 지금 목적은 "Redis로 토큰을 무효화하는 방법"을 배우는 것이지, Spring Security 설정을 배우는 것이 아니다.

#### Rate Limiting Lua 스크립트 원자성 검증

Sliding Window Rate Limiting에서 `ZADD` + `ZCOUNT` + `ZREMRANGEBYSCORE` 3개 명령을
Lua 스크립트로 묶어 원자적으로 실행한다.
Phase 2에서 Mutex Lock으로 단일 DB 조회를 보장했던 것처럼,
**원자성이 왜 필요한지를 테스트로 먼저 증명**하고 Lua로 해결하는 TDD 흐름을 유지한다.

---

## 3. Phase 2 → Phase 6 전체 로드맵 관점에서의 위치

```
Phase 1: Cache Aside + Write Back — "DB를 직접 치면 어떤 일이 벌어지나?"
Phase 2: Stampeding Herd 해결 + 특수 자료구조 — "캐시 만료 폭탄을 어떻게 막나?" ← 완료
Phase 3: Rate Limiting + JWT Blacklist — "악성 트래픽을 Redis로 어떻게 차단하나?"
Phase 4: Lua + Redlock + WATCH — "동시 송금 시 잔액 정합성을 어떻게 보장하나?"
Phase 5: Pub/Sub + Streams — "알림과 정산 이벤트를 어떻게 처리하나?"
Phase 6: 성능 분석 — "Hot Key, Big Key, Slow Query를 어떻게 찾아 해결하나?"
```

Phase 2까지 완료된 시점의 기술 스택 현황:

| 영역 | Phase 1~2에서 확립된 것 | Phase 3~6에서 추가될 것 |
|------|------------------------|------------------------|
| Redis 템플릿 | `StringRedisTemplate` (String 값) / `RedisTemplate<String, Object>` (객체) | Phase 3~4에서도 동일 원칙 유지 |
| 자료구조 | String, ZSET, HyperLogLog, Geo | Phase 3: String/ZSET(RateLimit), Phase 4: Lua, Phase 5: Streams |
| 테스트 도구 | `@SpringBootTest` + Testcontainers, `@WebMvcTest` + MockMvcTester, `@MockitoSpyBean` | Phase 3: 단위 테스트 비중 증가, Clock 추상화 추가 |
| 동시성 검증 | CountDownLatch + ExecutorService (Stampeding Herd) | Phase 4: 이중 출금 시나리오에서 재사용 |
| 분산 락 | Redisson RLock (Mutex Lock 전략) | Phase 4: Redlock (다중 Redis 노드) |

Phase 2에서 Redisson을 Mutex Lock 용도로 이미 도입했다.
Phase 4의 분산 락(Redlock)은 Phase 2의 단일 노드 락에서 **다중 Redis 노드로 확장**되는 자연스러운 흐름이다.
Phase 2에서 `RLock` 동작 방식을 이미 이해했으므로 Phase 4의 학습 비용이 낮아진다.

---

## 4. 수치 목표 (Phase 3 완료 기준)

Phase 2에서 K6 비교 수치를 기록하지 못한 반성을 바탕으로,
Phase 3는 각 기능 완료 시 측정 수치를 이 문서에 기록한다.

| 항목 | 측정 방법 | 목표 수치 |
|------|----------|---------|
| Fixed Window 경계 버그 | 테스트로 재현 | 경계 2초간 10회 통과 확인 |
| Sliding Window 정확도 | 테스트로 검증 | 60초 윈도우 내 정확히 5회만 허용 |
| JWT Blacklist 응답 지연 | `curl` 측정 | < 5ms (Redis 조회 1회) |
| Rate Limiting 추가 후 CPU | Prometheus | 추가 전 대비 < 5% 증가 |

---

> 다음 단계: [PLAN-phase3-Security.md](PLAN-phase3-Security.md) 작성 후 구현 시작
