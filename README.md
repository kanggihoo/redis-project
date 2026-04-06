# WePay — 간편송금·정산 시스템으로 배우는 Redis

> **핵심 철학:** 핀테크 도메인에서 자연스럽게 Redis의 6대 기능이 필요해지는 상황을 만들고, 각 Phase에서 **문제를 직접 눈으로 확인**한 뒤 Redis로 해결한다.

## 경험 내용

### 1. 캐싱 전략 (Caching Strategies)

단순 저장을 넘어 데이터 정합성 유지가 핵심.

- **Look Aside & Write Back**: 상황에 맞는 읽기/쓰기 전략 구현
- **Cache Aside 패턴**: DB 부하 감소를 위한 적절한 TTL(Time To Live) 설정
- **Stampeding Herd 문제 해결**: 대규모 트래픽에서 캐시 만료 시 동시에 DB로 몰리는 현상 방지

### 2. 메시징 모델 (Pub/Sub & Streams)

두 모델의 결정적인 차이인 **'데이터 보존 여부'**를 직접 체감하는 것이 중요.

- **Pub/Sub**: 실시간 채팅, 일회성 알림 전송 (Push 방식)
- **Streams**: 소비자 그룹(Consumer Group)을 활용한 메시지 분산 처리 및 장애 복구(ACK) 구현 (Kafka와 유사한 로그 구조)

### 3. 동시성 제어 (Transaction & Locking)

Redis의 싱글 스레드 특성을 활용해 분산 환경의 데이터 격차를 해결.

- **Lua 스크립트**: 여러 명령어를 원자성(Atomicity) 있게 실행하여 중간에 다른 연산이 끼어들지 못하게 방지
- **분산 락(Redlock)**: 여러 서버 인스턴스에서 동일한 자원에 접근할 때 `Redisson` 등을 활용해 정교하게 제어
- **Optimistic Lock**: `WATCH` 명령어를 사용한 낙관적 락 구현

### 4. 보안 및 트래픽 제어

- **API Rate Limiting**: Fixed Window, Sliding Window 알고리즘을 Redis 카운터로 구현하여 DDoS 및 어뷰징 방지
- **Token Blacklist**: 로그아웃된 JWT의 잔여 시간만큼 Redis에 저장해 무효화 처리
- **Session Management**: Spring Session Redis 등을 활용한 무상태(Stateless) 서버의 세션 공유

### 5. 특수 자료구조 활용

- **Sorted Set (ZSET)**: 실시간 랭킹 시스템(게임 스코어, 인기 검색어) 구현
- **HyperLogLog**: 대용량 데이터에서 중복을 제거한 방문자 수(UV) 카운트 (메모리 극가성비)
- **Geospatial**: 위치 기반 서비스(주변 맛집 찾기, 배달 거리 계산)

### 6. 성능 분석 및 디버깅 (프로메테우스 연동)

**`redis_exporter`** 를 사용해 "Redis가 느리다"는 상황을 재현하고 원인을 찾는 훈련.

> [!warning] MONITOR 사용 주의
> `MONITOR` / `redis-cli --stat`은 실시간 트래픽 관찰에 유용하지만, 운영 환경에서는 부하 문제가 있어 주의 필요

- **`SLOWLOG`**: 임계치 이상 걸린 커맨드를 기록 — 어떤 커맨드가 병목인지 파악
- **`MONITOR` / `redis-cli --stat`**: 실시간 트래픽 관찰
- **Big Key / Hot Key 탐지**: `redis-cli --bigkeys`, `--hotkeys` 옵션. 하나의 키가 수백 MB이거나 특정 키에 요청이 몰리면 싱글 스레드 특성상 전체가 블로킹됨
- **`OBJECT ENCODING`**: 같은 자료구조라도 내부 인코딩(listpack, skiplist 등)에 따라 메모리/성능 특성이 다름

---

## 도메인 개요

```
WePay — P2P 간편송금 + 소규모 가맹점 정산
├── 사용자: 잔액 충전, 송금, 결제
├── 가맹점: 결제 수신, 정산 요청
└── 관리자: 거래 모니터링, 이상 탐지
```

### 왜 이 도메인인가

> [!important] 도메인 선택 이유
>
> - 데이터가 **숫자(잔액, 거래 금액)** 뿐 → 크롤링/이미지 관리 불필요
> - 기존 결제 프로젝트(Kafka/Outbox)와 **동일 핀테크 도메인** → 포트폴리오 시너지
> - 모든 Redis 기능이 **억지가 아니라 자연스럽게 필요**해지는 구조
> - AI 시대에도 금융 규제·정산 로직·이중 지급 방지 도메인 지식은 대체 불가

### 사전 작업이 거의 없는 이유

```
필요한 데이터:
├── 유저 계좌: DB seed (INSERT 10~50개)
├── 가맹점: Mock 데이터 5~10개
├── 거래 내역: k6 부하 테스트로 자동 생성
└── 환율: 고정값 or 공공 API 1개
❌ 크롤링 없음 | ❌ 이미지 없음 | ❌ 외부 연동 최소
```

---

## 🛠 기술 스택

### Frontend (BFF)

- **Framework**: Next.js (App Router)
- **Language**: TypeScript
- **역할**: BFF(Backend For Frontend) — 화면에 필요한 데이터 준비 + 인증 관리

### Backend (Core API)

- **Framework**: Spring Boot 4.0.5
- **Language**: Java 21 (LTS)
- **Libraries**: Spring Data JPA, Spring Data Redis, Redisson, Micrometer Prometheus
- **Testing**: JUnit 5, Testcontainers, CountDownLatch + ExecutorService
- **역할**: 핵심 비즈니스 로직 + Redis + DB 전담

### Infrastructure (Docker Compose)

- **RDBMS**: PostgreSQL 17-alpine
- **Redis**: Redis 7.2-alpine (단일 → Phase 5에서 3대 클러스터)
- **Monitoring**: Prometheus + Grafana + redis_exporter
- **Load Testing**: k6

### 풀스택 아키텍처 — BFF 패턴

```
┌─────────────────────────────────────────────────────────┐
│  사용자 브라우저                                          │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│  Next.js (BFF 역할)                                      │
│  ├── Server Components: SSR 렌더링                       │
│  ├── API Routes / Server Actions:                        │
│  │   ├── 인증 프록시 (JWT → HttpOnly 쿠키 변환)           │
│  │   ├── API 조합 (Spring API 여러 개 → 화면에 맞게 병합) │
│  │   ├── 입력 Validation (Zod — 잘못된 요청 사전 차단)    │
│  │   └── 에러 정규화 (Spring 에러 → 사용자 친화적 메시지)  │
│  └── Middleware: 라우트 보호, 리다이렉트                   │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTP (내부 네트워크)
┌──────────────────────▼──────────────────────────────────┐
│  Spring Boot (핵심 비즈니스 로직)                          │
│  ├── 도메인 로직: 송금, 잔액 차감, 정산 계산               │
│  ├── Redis 연동: 캐싱, 락, Pub/Sub, Streams 전부          │
│  ├── DB 트랜잭션: PostgreSQL CRUD, 정합성 보장             │
│  ├── 보안: Rate Limiting, JWT 발급/검증                   │
│  └── 모니터링: Prometheus 메트릭 노출                     │
└─────────────────────────────────────────────────────────┘
```

### Next.js vs Spring 역할 분리 기준

| 기준            | Next.js (BFF)                                          | Spring Boot (Core API)          |
| --------------- | ------------------------------------------------------ | ------------------------------- |
| **핵심 원칙**   | "화면에 필요한 데이터를 준비"                          | "비즈니스 규칙을 실행"          |
| **인증**        | 쿠키/세션 관리, JWT → HttpOnly 쿠키 변환               | JWT 발급/검증, 권한 체크        |
| **데이터 가공** | Spring API 여러 개 조합 → 한 화면에 맞게 병합          | 도메인 단위의 순수한 REST API   |
| **입력 검증**   | Zod로 사전 차단 (잘못된 요청이 Spring까지 가지 않도록) | 비즈니스 규칙 검증 (잔액, 한도) |
| **에러 처리**   | Spring 에러 코드를 사용자 친화적 메시지로 변환         | 비즈니스 에러 코드 반환         |
| **Redis**       | ❌ 직접 안 건드림                                      | ✅ 전부 여기서 처리             |
| **DB**          | ❌ 직접 안 건드림                                      | ✅ 전부 여기서 처리             |

> [!tip] BFF 패턴을 쓰는 이유
>
> - 실무에서 실제로 많이 쓰는 패턴 (토스, 배민 등)
> - API 키나 JWT를 클라이언트에 직접 노출하지 않는 보안 계층 분리
> - 프론트엔드 역량 + 아키텍처 설계 역량을 동시에 증명

---

## 🔗 문제 → 해결 → 새로운 문제 체인

```
Phase 1: 기본 캐싱 — Cache Aside + TTL + Write Back
  └─ 발견된 문제: 캐시 만료 시 대량 트래픽이 DB로 몰림 (Stampeding Herd)
       │
Phase 2: Stampeding Herd 해결 + 특수 자료구조 (ZSET, HyperLogLog, Geo)
  └─ 해결: Mutex Lock / Logical Expiration
  └─ 발견된 문제: 송금 API에 매크로/어뷰징 공격, 인증 토큰 관리 필요
       │
Phase 3: 보안 및 트래픽 제어 (Rate Limiting, JWT Blacklist, Session)
  └─ 해결: API 호출 제한, 토큰 무효화
  └─ 발견된 문제: 동시 송금 시 잔액 이중 차감, 중복 송금 발생
       │
Phase 4: 동시성 제어 (Lua + Redlock + WATCH)
  └─ 해결: 원자적 잔액 차감, 분산 락
  └─ 발견된 문제: 송금 완료 알림이 유실되거나, 정산 이벤트 처리에 장애 발생
       │
Phase 5: 메시징 모델 (Pub/Sub + Streams)
  └─ 해결: 실시간 알림 + 유실 불허 이벤트 파이프라인
  └─ 발견된 문제: 트래픽 증가에 따른 Redis 병목 (Hot Key, Big Key, Slow Query)
       │
Phase 6: 성능 분석 및 트러블슈팅
  └─ 해결: Hot Key 분산, Multi-level Caching, 명령어 최적화
  └─ 최종 상태: 모니터링 기반 운영 안정
```

---

## 비교 시나리오 (전 Phase 공통 도메인)

```
간편송금 — 잔액 충전 + P2P 송금 + 가맹점 결제

POST /api/transfer     → 잔액 확인 → 출금 → 입금 → 거래 내역 저장
GET  /api/accounts/:id → 잔액 조회 (캐싱 대상)
GET  /api/ranking      → 거래 빈도 Top 가맹점
GET  /api/stores/nearby → 주변 가맹점 검색
```

> Phase 1~2는 조회/캐싱 중심, Phase 3부터 보안, Phase 4부터 송금 동시성, Phase 5부터 메시징이 추가된다.

---

## Phase 1. 기본 캐싱 전략 — Cache Aside + Write Back

> "모든 요청이 DB를 직접 치면 어떤 일이 벌어지는가?"

### 구현 대상

- **Cache Aside (읽기)**: 계좌 잔액 조회, 가맹점 목록, 환율 정보
  - 캐시 미스 → DB 조회 → Redis 저장 (TTL 60초)
  - 캐시 히트 → Redis에서 바로 반환
- **Write Back (쓰기 지연)**: 가맹점별 거래 건수, 조회수 카운터
  - Redis `INCR`로 실시간 카운트 → 1분 주기 배치로 DB 벌크 업데이트

### 테스트로 유발할 문제

| #   | 시나리오              | 방법                          | 관찰할 현상                           |
| --- | --------------------- | ----------------------------- | ------------------------------------- |
| 1   | **캐시 없이 부하**    | k6 200VU로 잔액 조회 API 호출 | DB 쿼리 폭증, 응답 시간 급증          |
| 2   | **캐시 적용 후 비교** | 동일 시나리오 반복            | Cache Hit Ratio, DB 쿼리 수 감소 확인 |
| 3   | **캐시 무효화 누락**  | 잔액 변경 후 캐시 미삭제      | 이전 잔액이 계속 보이는 정합성 문제   |

### 모니터링으로 확인하는 것

- `cache.hit.ratio` → Cache Aside 적용 전후 비교
- `db.query.count` → DB 부하 감소량 측정
- `redis.memory.used` → 캐시 메모리 사용 추이

### 이 Phase에서 얻는 인사이트

- Cache Aside vs Write Back의 구분 기준: **읽기 주도 vs 쓰기 주도**
- TTL이 너무 짧으면 캐시 효과 없음, 너무 길면 정합성 문제
- Write Back의 데이터 유실 위험 (Redis 장애 시 카운터 소실)

### 측정 지표 (회고용)

- 캐시 적용 전후 평균 응답 시간 (ms)
- Cache Hit Ratio (%)
- DB 쿼리 수 감소율 (%)

### ❓ 남은 문제 → Phase 2로

> "TTL이 만료되는 순간, 200명이 동시에 DB를 조회한다. 월급날 잔액 조회 폭주 시 캐시가 만료되면?"

---

## Phase 2. Stampeding Herd 해결 + 특수 자료구조

> "캐시가 만료되는 순간 수만 건의 쿼리가 DB로 몰리는 것을 어떻게 막는가?"

### 이전 Phase의 문제를 어떻게 해결하는가

**Stampeding Herd 방지 — 3가지 기법 비교 구현:**

1. **Mutex Lock (Redisson)**: 첫 번째 요청만 DB 조회 → 캐시 갱신, 나머지는 대기
2. **Logical Expiration**: 물리적 TTL은 길게, 논리적 만료 시간을 데이터에 포함 → 백그라운드 갱신
3. **TTL Jitter**: 만료 시간에 랜덤값 추가 → 동시 만료 방지

### 특수 자료구조 적용

| 자료구조              | 핀테크 적용                       | Redis 명령어            |
| --------------------- | --------------------------------- | ----------------------- |
| **Sorted Set (ZSET)** | 거래 빈도 Top 10 가맹점 랭킹      | `ZINCRBY`, `ZREVRANGE`  |
| **Sorted Set (ZSET)** | 최근 거래 내역 (타임스탬프 Score) | `ZADD`, `ZRANGEBYSCORE` |
| **HyperLogLog**       | 일별 활성 거래자 수 (DAU)         | `PFADD`, `PFCOUNT`      |
| **Geospatial**        | 내 주변 가맹점 찾기               | `GEOADD`, `GEORADIUS`   |

### 테스트로 유발할 문제

| #   | 시나리오                 | 방법                                | 관찰할 현상                                      |
| --- | ------------------------ | ----------------------------------- | ------------------------------------------------ |
| 1   | **Stampeding Herd 재현** | TTL 직후 k6 500VU 동시 조회         | DB 쿼리 폭증 vs Mutex Lock 적용 후 1건만 DB 도달 |
| 2   | **ZSET 랭킹 정확성**     | 1000건 거래 발생 후 Top 10 확인     | 실시간 랭킹 반영 속도                            |
| 3   | **HyperLogLog 오차율**   | 동일 유저 100번 거래 → PFCOUNT 확인 | UV = 1 확인, Set 대비 메모리 비교                |

### 모니터링으로 확인하는 것

- Stampeding Herd 발생 시 DB 쿼리 수 vs 해결 후 쿼리 수
- ZSET 랭킹 갱신 지연 시간
- HyperLogLog vs Set 메모리 사용량 비교

### 이 Phase에서 얻는 인사이트

- Stampeding Herd 방지 3가지 기법의 트레이드오프
- ZSET이 O(log N)으로 실시간 랭킹에 최적인 이유
- HyperLogLog의 12KB 고정 메모리 vs Set의 선형 증가

### 측정 지표 (회고용)

- Stampeding Herd 시 DB 쿼리 수: 방지 전 *건 → 방지 후 *건
- HyperLogLog 메모리: \_KB vs Set 메모리: \_KB (동일 데이터)
- Geo 쿼리 응답 시간 (ms)

### ❓ 남은 문제 → Phase 3로

> "송금 API에 매크로 봇이 초당 100회 호출한다. 로그아웃해도 JWT가 계속 유효하다."

---

## Phase 3. 보안 및 트래픽 제어

> "악의적 트래픽을 Redis로 어떻게 차단하는가?"

### 구현 대상

#### 3-1. API Rate Limiting — Fixed Window vs Sliding Window

```
송금 API: 1분 5회 제한 (엄격)
잔액 조회 API: 1분 100회 제한 (느슨)
```

- **Fixed Window**: Redis `INCR` + `EXPIRE` → 경계 조건 버그 직접 확인
- **Sliding Window**: Redis `ZSET` timestamp 기반 → 정밀한 윈도우 제어

#### 3-2. JWT Token Blacklist

```redis
-- 로그아웃 시 JWT 무효화 (잔여 유효시간만큼 TTL)
SET blacklist:jwt:<jti> 1 EX <잔여초>
```

#### 3-3. Session Management

- Spring Session Redis로 다중 서버 인스턴스 간 세션 공유
- Stateless 서버 환경에서의 인증 상태 관리

### 테스트로 유발할 문제

| #   | 시나리오                | 방법                                    | 관찰할 현상                                      |
| --- | ----------------------- | --------------------------------------- | ------------------------------------------------ |
| 1   | **Rate Limit 경계**     | Fixed Window 경계 시점에 60회 연속 호출 | 59초에 5회 + 61초에 5회 = 2초간 10회 통과 (버그) |
| 2   | **Sliding Window 비교** | 동일 시나리오                           | 정확히 1분 내 5회만 허용                         |
| 3   | **Blacklist 검증**      | 로그아웃 후 동일 JWT로 API 호출         | 즉시 거부 확인                                   |

### 이 Phase에서 얻는 인사이트

- Fixed Window의 경계 조건 버그가 왜 위험한가
- Sliding Window가 더 정밀하지만 메모리를 더 사용하는 트레이드오프
- JWT Blacklist의 TTL = 토큰 잔여 시간으로 메모리 자동 정리

### 측정 지표 (회고용)

- Fixed vs Sliding Window 경계 조건 통과율 차이
- Rate Limiting 추가 후 악성 트래픽 차단률 (%)
- Blacklist 조회 지연 (ms)

### ❓ 남은 문제 → Phase 4로

> "보안은 잡았는데, 동시에 같은 계좌에서 송금하면 잔액이 마이너스가 된다."

---

## Phase 4. 동시성 제어 — Lua + Redlock + WATCH

> "100명이 동시에 같은 계좌에서 송금하면 잔액이 정확해야 한다."

### 구현 대상

#### 4-1. Lua 스크립트 — 잔액 확인 + 차감 원자 처리

```lua
-- 이중 출금 방지: 잔액 확인 → 차감을 원자적으로
local balance = tonumber(redis.call('GET', KEYS[1]))
local amount = tonumber(ARGV[1])
if balance >= amount then
    redis.call('DECRBY', KEYS[1], amount)
    return 1  -- 성공
end
return 0  -- 잔액 부족
```

#### 4-2. 분산 락 (Redlock) — 동일 계좌 동시 송금 방지

- 멀티탭에서 버튼 연타 → 여러 WAS에 요청 분산
- Redisson 분산 락으로 **같은 계좌의 동시 출금을 단 1건만 처리**

#### 4-3. Optimistic Lock (WATCH) — 충전 중 동시 송금 방지

```redis
WATCH account:balance:user_1
-- 잔액 읽기
val = GET account:balance:user_1
MULTI
SET account:balance:user_1 (val - amount)
EXEC
-- 다른 클라이언트가 잔액을 변경했으면 EXEC 실패 → 재시도
```

### 테스트로 유발할 문제

| #   | 시나리오                | 방법                                       | 검증 기준                                        |
| --- | ----------------------- | ------------------------------------------ | ------------------------------------------------ |
| 1   | **이중 출금**           | 잔액 10만원, 100스레드가 동시에 1만원 송금 | Lua 적용 전: 잔액 마이너스 / 적용 후: 정확히 0원 |
| 2   | **분산 환경 동시 송금** | Docker로 WAS 3대 실행, 동일 계좌 동시 송금 | Redlock으로 1건만 처리                           |
| 3   | **충전+송금 충돌**      | 충전과 송금이 동시에 발생                  | WATCH 실패 시 재시도로 정합성 보장               |

### 이 Phase에서 얻는 인사이트

- Lua의 원자성이 MULTI/EXEC보다 안전한 이유 (중간에 다른 명령 불가)
- Redlock vs 단일 Redis 락의 차이 (Redis 장애 시 안전성)
- WATCH의 낙관적 접근: 충돌 적을 때 성능 우수, 충돌 많으면 재시도 폭증

### 측정 지표 (회고용)

- Lua 적용 전후 잔액 정합성 (불일치 건수)
- Redlock 락 획득 대기 시간 (ms)
- WATCH 재시도 횟수 분포

### ❓ 남은 문제 → Phase 5로

> "송금은 정확한데, 송금 완료 알림이 안 온다. 정산 이벤트가 유실되면 가맹점 돈이 안 들어온다."

---

## Phase 5. 메시징 모델 — Pub/Sub vs Streams

> "실시간 알림과 유실 불허 이벤트를 어떻게 구분해서 처리하는가?"

### 구현 대상

#### 5-1. Pub/Sub — 송금 완료 실시간 알림 (유실 허용)

```redis
PUBLISH transfer:notify:user_123 "user_456님이 50,000원을 보냈습니다"
```

- 접속 중인 유저에게만 전달, 놓쳐도 무방
- WebSocket/SSE와 연동하여 프론트 실시간 반영

#### 5-2. Streams — 정산 이벤트 파이프라인 (유실 불허)

```redis
-- 거래 완료 이벤트 발행
XADD settlement:events * merchantId M001 amount 50000 txId TX123

-- Consumer Group으로 분산 처리
XREADGROUP GROUP settlement-workers worker1 COUNT 1 STREAMS settlement:events >

-- 처리 완료 후 ACK
XACK settlement:events settlement-workers <message-id>
```

- 가맹점 정산 금액 집계 / 수수료 계산 / 정산 확정
- 워커 장애 시 `XPENDING` → 미처리 메시지 재할당

### 테스트로 유발할 문제

| #   | 시나리오                | 방법                              | 관찰할 현상                            |
| --- | ----------------------- | --------------------------------- | -------------------------------------- |
| 1   | **Pub/Sub 메시지 유실** | 구독자 없는 상태에서 PUBLISH      | 메시지 영구 소실 — 직접 체감           |
| 2   | **Streams 장애 복구**   | Consumer 워커 강제 종료 후 재시작 | XPENDING으로 미처리 메시지 재처리 확인 |
| 3   | **Consumer Group 분산** | 워커 3대로 1000건 이벤트 처리     | 각 워커의 처리 건수 분산 확인          |

### 이 Phase에서 얻는 인사이트

- Pub/Sub vs Streams의 결정적 차이: **데이터 보존 여부**
- Streams가 Kafka와 유사한 로그 구조인 이유
- Consumer Group의 ACK/NACK 패턴이 장애 복구에 필수인 이유

### 측정 지표 (회고용)

- Pub/Sub 메시지 유실률 (구독자 부재 시)
- Streams 처리 지연 (이벤트 발행 → ACK까지 ms)
- Consumer Group 분산 균등도

### ❓ 남은 문제 → Phase 6로

> "기능은 다 동작하는데, 월급날 트래픽이 10배 증가하면 Redis가 느려진다. 어디가 병목인가?"

---

## Phase 6. 성능 분석 및 트러블슈팅

> "Redis가 느리다는 상황을 재현하고, 원인을 찾아 해결하는 훈련."

### 구현 대상

#### 6-1. Hot Key 탐지 및 해결

- k6로 특정 가맹점(인기 가맹점) 잔액 키에 트래픽 집중
- `redis-cli --hotkeys`로 확인
- **해결**: 서버 로컬 캐시(Caffeine) 결합 → **Multi-level Caching**

#### 6-2. Big Key 탐지 및 해결

- 거래 내역을 하나의 키에 계속 추가 → 수백 MB 키 생성
- `redis-cli --bigkeys`로 확인
- **해결**: 키 분할 (월별/일별 파티셔닝)

#### 6-3. SLOWLOG 분석

- 대규모 ZSET(100만 건 랭킹)의 ZRANGEBYSCORE 전체 범위 검색
- `SLOWLOG GET 10`으로 느린 명령어 식별
- **해결**: 범위 한정, COUNT 옵션 추가

#### 6-4. OBJECT ENCODING 관찰

```redis
-- 작은 ZSET vs 큰 ZSET의 내부 인코딩 차이
OBJECT ENCODING ranking:daily
-- 10개: "listpack" (메모리 효율) vs 200개: "skiplist" (성능 우선)
```

#### 6-5. Grafana 대시보드 완성

- `redis_exporter` → Prometheus → Grafana
- 핵심 패널: 메모리 사용량, Cache Hit/Miss Ratio, 명령 실행 지연, 커넥션 수

### 테스트 시나리오

| 시나리오                   | 사용 도구             | 해결 방법                              |
| -------------------------- | --------------------- | -------------------------------------- |
| 인기 가맹점 키에 요청 집중 | `redis-cli --hotkeys` | Multi-level Caching (Caffeine + Redis) |
| 거래 내역 키 비대화        | `redis-cli --bigkeys` | 키 파티셔닝 (월별 분리)                |
| 랭킹 ZSET 전체 스캔        | `SLOWLOG GET`         | 범위 한정 + LIMIT 옵션                 |
| 인코딩 전환 임계점 확인    | `OBJECT ENCODING`     | ziplist → skiplist 전환 시점 파악      |
| 전체 Redis 상태 시각화     | Grafana 대시보드      | 메모리/지연/히트율 실시간 모니터링     |

### 이 Phase에서 얻는 인사이트

- Hot Key 하나가 싱글 스레드 Redis 전체를 블로킹할 수 있는 이유
- Multi-level Caching의 L1(로컬) + L2(Redis) 전략
- `SLOWLOG`가 운영 환경에서 가장 먼저 확인해야 할 도구인 이유

---

## 전체 기술 도입 흐름 요약

| Phase | 핵심 주제            | Redis 기능                                                 | 해결하는 문제                     | 발견하는 새 문제            |
| ----- | -------------------- | ---------------------------------------------------------- | --------------------------------- | --------------------------- |
| 1     | 기본 캐싱            | Cache Aside, Write Back, TTL                               | DB 부하 감소                      | Stampeding Herd             |
| 2     | 캐시 고급 + 자료구조 | Mutex Lock, ZSET, HyperLogLog, Geo                         | 캐시 만료 폭주, 랭킹/UV/위치 검색 | 매크로 공격, 인증 관리      |
| 3     | 보안·트래픽 제어     | Rate Limiting (ZSET), JWT Blacklist, Session               | 어뷰징 차단, 토큰 무효화          | 동시 송금 시 잔액 불일치    |
| 4     | 동시성 제어          | Lua Script, Redlock, WATCH                                 | 이중 출금 방지, 분산 락           | 알림 유실, 이벤트 처리 장애 |
| 5     | 메시징               | Pub/Sub, Streams (Consumer Group, ACK)                     | 실시간 알림, 정산 파이프라인      | 트래픽 증가 시 Redis 병목   |
| 6     | 성능 분석            | SLOWLOG, hotkeys, bigkeys, OBJECT ENCODING, redis_exporter | Hot Key, Big Key, 느린 쿼리 해결  | - (운영 안정)               |

---

## 기능 ↔ Redis 매핑 요약

| 기능                                  | Redis 항목                                                        |
| ------------------------------------- | ----------------------------------------------------------------- |
| 계좌 잔액 조회, 가맹점 목록, 환율     | Cache Aside + TTL                                                 |
| 가맹점 거래 건수, 조회수              | Write Back (INCR → 배치 flush)                                    |
| 캐시 만료 시 DB 폭증 방지             | Stampeding Herd 방지 (Mutex Lock, Logical Expiration, TTL Jitter) |
| 송금 완료 실시간 알림                 | Pub/Sub                                                           |
| 정산 이벤트 (유실 불허)               | Streams (Consumer Group + ACK)                                    |
| 잔액 확인 + 차감 원자 처리            | Lua Script                                                        |
| 동일 계좌 동시 송금 방지 (분산 환경)  | Redlock (Redisson)                                                |
| 충전 중 동시 송금 충돌                | WATCH + MULTI/EXEC                                                |
| 송금 API 호출 제한                    | Rate Limiting (Fixed/Sliding Window)                              |
| 로그아웃 JWT 무효화                   | Token Blacklist (SET + TTL)                                       |
| 다중 서버 세션 공유                   | Spring Session Redis                                              |
| 거래 빈도 Top 가맹점 / 최근 거래 내역 | Sorted Set (ZSET)                                                 |
| 일별 활성 거래자 수 (DAU)             | HyperLogLog                                                       |
| 주변 가맹점 찾기                      | Geospatial                                                        |
| Redis 병목 탐지                       | SLOWLOG, hotkeys, bigkeys, OBJECT ENCODING                        |
| Redis 상태 시각화                     | redis_exporter + Prometheus + Grafana                             |

---

## Phase별 핵심 Grafana 패널

| Phase | 핵심 지표                                 | 무엇을 보는가               |
| ----- | ----------------------------------------- | --------------------------- |
| 1     | `cache.hit.ratio`, `db.query.count`       | 캐시 적용 전후 DB 부하 변화 |
| 2     | Stampeding Herd 시 DB 쿼리 수             | Mutex Lock 적용 전후 비교   |
| 3     | `ratelimit.blocked.count`                 | 매크로 트래픽 차단 현황     |
| 4     | `transfer.duplicate.blocked`, 잔액 정합성 | 이중 출금 차단 건수         |
| 5     | Streams Consumer Lag, PEL 크기            | 정산 이벤트 처리 지연       |
| 6     | Redis 메모리, SLOWLOG 수, Hit/Miss Ratio  | 전체 Redis 상태             |

---

## 학습 완료 시 답할 수 있는 질문들

> "Cache Aside와 Write Back은 언제 구분해서 쓰나?"
> → Phase 1에서 읽기 주도(잔액 조회)에 Cache Aside, 쓰기 주도(카운터)에 Write Back을 적용하며 차이를 체감했습니다.

> "캐시 만료 시 DB로 트래픽이 몰리는 걸 어떻게 막나?"
> → Phase 2에서 Mutex Lock으로 DB 쿼리를 \_건에서 1건으로 줄이고, TTL Jitter로 동시 만료를 방지했습니다.

> "Rate Limiting에서 Fixed Window의 문제점은?"
> → Phase 3에서 경계 시점에 2배 트래픽이 통과하는 버그를 직접 확인하고, Sliding Window로 전환했습니다.

> "동시 송금 시 이중 차감을 어떻게 막나?"
> → Phase 4에서 Lua 스크립트로 잔액 확인+차감을 원자적으로 처리하고, 분산 환경에서는 Redlock을 적용했습니다.

> "Pub/Sub와 Streams의 차이는?"
> → Phase 5에서 Pub/Sub은 구독자 없으면 메시지 유실, Streams는 ACK 기반으로 재처리 가능한 것을 직접 확인했습니다.

> "Redis에서 특정 키가 병목이면 어떻게 찾나?"
> → Phase 6에서 `--hotkeys`로 Hot Key를 식별하고, Multi-level Caching으로 해결하여 응답시간이 \_ms에서 \_ms로 개선됐습니다.
