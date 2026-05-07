# Project Brief

## 프로젝트 목적

WePay는 P2P 간편송금과 소규모 가맹점 정산 도메인에서 Redis의 핵심 기능을 단계적으로 학습하고 검증하는 프로젝트다.

핵심 목표는 기능을 단순히 붙이는 것이 아니라, 각 Phase에서 먼저 문제가 발생하는 상황을 코드와 테스트로 확인한 뒤 Redis 기능으로 해결하는 것이다.

## 장기 로드맵

1. Phase 1: 기본 캐싱 전략
   - Cache Aside, Write Back, Null Caching, Cache Invalidation, TTL 실험
   - Stampeding Herd 문제를 재현하여 Phase 2의 출발점으로 만든다.
2. Phase 2: Stampeding Herd 해결 + 특수 자료구조
   - Mutex Lock, Logical Expiration, TTL Jitter
   - ZSET, HyperLogLog, Geo를 핀테크 도메인에 적용한다.
3. Phase 3: 보안 및 트래픽 제어
   - Rate Limiting, JWT Blacklist, Session Management
   - 매크로/어뷰징 호출과 로그아웃 후 토큰 재사용 문제를 Redis로 차단한다.
4. Phase 4: 동시성 제어
   - Lua Script, Redlock, WATCH
   - 동시 송금 시 잔액 이중 차감과 중복 송금을 방지한다.
5. Phase 5: 메시징 모델
   - Pub/Sub, Streams, Consumer Group, ACK
   - 유실 허용 알림과 유실 불허 정산 이벤트를 분리한다.
6. Phase 6: 성능 분석 및 트러블슈팅
   - SLOWLOG, hotkeys, bigkeys, OBJECT ENCODING, redis_exporter
   - Redis 병목을 관측하고 완화한다.

## 도메인 경계

- 사용자: 잔액 충전, 잔액 조회, P2P 송금
- 가맹점: 결제 수신, 거래 빈도 랭킹, 위치 기반 검색, 정산 대상 이벤트
- 관리자: 거래/Redis 상태 모니터링, 이상 탐지

## 기술 스택

- Backend: Spring Boot 4.0.5, Java 21
- Persistence: PostgreSQL 17-alpine, Spring Data JPA
- Redis: Redis 7.2-alpine, Spring Data Redis, Redisson 4.3.0
- Observability: Actuator, Micrometer Prometheus, redis_exporter, Prometheus, Grafana
- Test: JUnit 5, Testcontainers, Mockito Bean Override API, MockMvcTester
- Load Test: k6

## 우선순위

- 현재 코드와 테스트를 문서보다 우선한다.
- Redis 기능은 도메인 문제와 연결해서 도입한다.
- Phase별 테스트와 트러블슈팅 기록을 남겨 학습 포인트가 재현 가능해야 한다.
- Next.js BFF는 장기 아키텍처에 포함되어 있지만 현재 구현 범위는 Spring Boot Core API 중심이다.
