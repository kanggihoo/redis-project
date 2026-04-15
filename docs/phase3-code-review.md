# Phase 3 테스트 코드 리뷰 리포트 (보안 및 트래픽 제어) -GEMINI

## 📝 리뷰 개요
Phase 3에서 구현된 Rate Limiting, JWT Blacklist, Session Management 관련 테스트 코드에 대한 상세 리뷰 결과입니다. 현재 모든 테스트는 **PASS** 상태이며, Testcontainers를 활용한 환경 격리가 잘 이루어져 있습니다.

---

## 🔍 테스트 코드별 상세 리뷰

### 1. Rate Limiting 서비스 (Fixed & Sliding Window)
*   **파일**: `FixedWindowRateLimitServiceTest`, `SlidingWindowRateLimitServiceTest`
*   **리뷰 내용**:
    *   **Good**: `isAllowed` 인터페이스를 통해 전략 패턴이 잘 적용되었으며, 각 윈도우 알고리즘의 핵심 로직을 명확히 검증함.
    *   **Improvement**: `@BeforeEach`에서 `stringRedisTemplate.keys("ratelimit:*")`를 사용함. `KEYS` 명령어는 운영 환경에서 성능 문제를 야기할 수 있으므로, 테스트 코드에서도 `SCAN` 기반이나 특정 키 삭제 방식으로 습관화하는 것이 권장됨.

### 2. Fixed Window 경계 조건 증명 (Bug PoC)
*   **파일**: `FixedWindowBoundaryBugTest`
*   **리뷰 내용**:
    *   **Good**: **매우 우수함.** 기술적 의사결정(왜 Sliding Window가 필요한가?)의 근거를 코드로 증명하여 팀의 기술 자산으로 승격시킴.
    *   **Improvement**: `Thread.sleep(2500)`과 같이 실제 시간에 의존함. CI/CD 서버 부하 시 타이밍 이슈로 테스트가 깨질(Flaky) 가능성이 있음. 추후 `Awaitility` 라이브러리 도입 고려 필요.

### 3. JWT 서비스 및 블랙리스트
*   **파일**: `JwtTokenServiceTest`
*   **리뷰 내용**:
    *   **Good**: Redis의 `getExpire`를 활용해 **블랙리스트 키의 TTL이 토큰 잔여 시간과 일치하는지** 검증한 점이 탁월함. 자원 효율성을 고려한 테스트 설계임.
    *   **Improvement**: 정상 흐름(Happy Path) 외에 "변조된 토큰", "만료된 토큰" 등 예외 상황(Negative Case)에 대한 검증 보완 필요.

### 4. 세션 관리 (Spring Session Redis)
*   **파일**: `SessionDemoServiceTest`
*   **리뷰 내용**:
    *   **Good**: `MockMvcTester`를 통해 실제 HTTP 쿠키 흐름과 Redis 내부 키(`spring:session:*`) 생성을 동시에 검증하여 인프라 정합성을 확보함.
    *   **Improvement**: 세션 타임아웃 발생 시나리오나 세션 불일치(Session Fixation) 관련 보안 테스트 추가 권장.

---

## 🚩 우선순위별 문제점 요약

| 우선순위 | 구분 | 내용 | 영향도 |
| :--- | :--- | :--- | :--- |
| **P0 (Critical)** | **성능/안정성** | `keys()` 명령어 사용 | 테스트 데이터 증가 시 Redis 블로킹 및 테스트 속도 저하 |
| **P1 (High)** | **테스트 품질** | `Thread.sleep()` 남용 | 빌드 시간 증가의 원인 (현재 약 1분 11초). 전체 Phase 완료 시 누적 지연 발생 |
| **P2 (Medium)** | **커버리지** | 예외 케이스(Negative) 부족 | 공격자 시나리오나 엣지 케이스에 대한 방어 로직 검증 미흡 |
| **P3 (Low)** | **유지보수** | 하드코딩된 설정값 | 테스트 코드 내 직접 주입된 상수가 많아 프로퍼티 변경 시 테스트 수정 공수 발생 |

---

## 💡 종합 의견 및 향후 전략

1.  **빌드 시간 최적화**: 현재 빌드 시간이 1분을 초과했습니다. Phase가 늘어날수록 선형적으로 증가할 것이므로, 시간을 조작하는 `Clock` 모킹이나 `Awaitility` 도입이 필요합니다.
2.  **보안 강화**: 보안이 테마인 Phase인 만큼, "잘못된 입력"에 대한 차단 테스트를 강화하여 Interceptor의 견고함을 증명해야 합니다.
3.  **다음 단계 진행**: 현재 수준으로도 Phase 3의 학습 목표는 충분히 달성되었습니다. **Phase 4 (Lua & Redlock)**로 진행하여 실제 동시성 문제를 해결하는 데 집중할 것을 권장합니다.


# Phase 3 서비스 계층 코드 리뷰 리포트

## 📝 리뷰 개요
Phase 3의 핵심 비즈니스 로직(Rate Limiting, JWT, Session) 서비스 코드에 대한 분석 결과입니다. 기능적 완성도는 높으나, 분산 환경에서의 **원자성(Atomicity)**과 **성능 최적화** 관점에서 개선이 필요합니다.

---

## 🔍 서비스별 상세 리뷰

### 1. FixedWindowRateLimitService
*   **현황**: `increment()` 호출 후 `count == 1`인 경우에만 `expire()` 호출.
*   **문제점 (Race Condition)**: `increment` 성공 후 `expire` 실패 시 해당 키가 Redis에 영구히 남는 메모리 누수 위험이 있음.
*   **우선순위**: **P0 (Critical)**

### 2. SlidingWindowRateLimitService
*   **현황**: 하나의 요청 처리 시 `removeRangeByScore`, `zCard`, `add`, `expire` 총 4회의 Redis 호출 발생.
*   **문제점 (Performance & Atomicity)**:
    *   4회 네트워크 왕복(Round-trip)으로 인한 응답 지연 발생.
    *   개수 확인(`zCard`)과 추가(`add`) 사이의 간극에서 동시성 이슈 발생 가능.
*   **우선순위**: **P1 (High)**

### 3. JwtTokenService
*   **현황**: `parseClaims()` 메서드가 여러 위치에서 중복 호출됨 (jti 추출, 만료 시간 확인 등).
*   **문제점 (Efficiency)**: JWT 파싱 및 서명 검증은 고비용 CPU 작업임. 중복 호출로 인해 전체적인 API 성능 저하 유발.
*   **우선순위**: **P2 (Medium)**

### 4. SessionDemoService
*   **현황**: 스프링 세션 연동의 추상화 계층 역할.
*   **특이사항**: 로직상 문제는 없으나, 세션에 저장되는 객체의 크기가 커질 경우 Redis 입출력 부하가 발생할 수 있음을 인지해야 함.

---

## 🏁 종합 진단 및 우선순위 요약

| 등급 | 항목 | 영향 | 해결 방안 (Phase 4 예고) |
| :--- | :--- | :--- | :--- |
| **P0** | **Redis 명령 원자성 부족** | 메모리 누수 및 정합성 깨짐 | **Lua Script**를 사용하여 명령 통합 |
| **P1** | **네트워크 오버헤드** | API 응답 시간(Latency) 증가 | Redis 파이프라이닝 또는 Lua Script 도입 |
| **P2** | **CPU 리소스 비효율** | 서버 처리량 저하 | Claims 객체 재사용 및 파싱 로직 최적화 |

## 💡 최종 제언
현재 코드는 단일 서버 환경이나 낮은 트래픽에서는 문제가 없으나, **"Redis의 진정한 강점은 원자적 처리에 있다"**는 관점에서 Phase 4의 Lua 스크립트 도입이 반드시 필요합니다. Phase 3의 학습을 통해 "왜 원자성이 필요한가"에 대한 근거를 확보했으므로, 다음 단계에서 이를 기술적으로 해결하는 과정을 추천합니다.
