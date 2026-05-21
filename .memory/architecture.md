# Architecture

## 현재 구현 구조

현재 코드는 Spring Boot Core API 중심이다. README에는 Next.js BFF가 장기 아키텍처로 포함되어 있지만, 현재 저장소에서 확인된 구현은 `wepay` Spring Boot 애플리케이션과 Docker 기반 인프라다.

## 주요 패키지

```text
com.example.wepay
  cache        -> Logical Expiration 래퍼
  config       -> RedisTemplate 설정
  controller   -> REST API
  domain       -> JPA Entity
  event        -> 캐시 무효화 이벤트/리스너
  repository   -> Spring Data JPA Repository
  service      -> Redis 학습 기능별 서비스
```

## 데이터 흐름

### 계좌 조회 캐싱

1. `AccountController`가 전략 파라미터를 받는다.
2. 선택된 `AccountCacheService` 구현체가 Redis를 먼저 확인한다.
3. 캐시 미스 또는 전략별 갱신 조건에서 `AccountRepository`로 DB를 조회한다.
4. Redis에 `account:{id}` 또는 전략별 래퍼 값을 저장한다.

### 잔액 변경 후 캐시 무효화

1. `AccountBalanceServiceImpl`이 잔액 변경 트랜잭션을 수행한다.
2. `AccountCacheEvictEvent`를 발행한다.
3. 커밋 성공 후 `AccountCacheEvictListener`가 캐시를 삭제한다.
4. 롤백 시 리스너가 실행되지 않아 기존 캐시가 보존된다.

### Write Back 카운터

1. 거래 발생 시 Redis `store:txcount:{storeId}`를 증가시킨다.
2. 배치 flush에서 Redis 값을 가져와 DB에 upsert한다.

### Phase 2 특수 자료구조

- `StoreRankingServiceImpl`: `store:ranking` ZSET으로 가맹점 거래 빈도 순위 관리
- `DailyActiveUserServiceImpl`: `dau:{yyyy-MM-dd}` HyperLogLog로 일별 활성 사용자 수 추정
- `StoreLocationServiceImpl`: `store:locations` Geo로 주변 가맹점 검색

## 외부 의존성

- PostgreSQL: 계좌와 거래 카운터의 영속 저장소
- Redis: 캐시, 락, 카운터, 특수 자료구조
- Redisson: Mutex Lock 전략
- Prometheus/Grafana/redis_exporter: Redis 및 애플리케이션 관측
- k6: Stampeding Herd 전략별 부하 테스트

## 위험한 경계

- `AccountCacheService` 구현체가 여러 개이므로 기본 구현체에는 `@Primary`가 필요하다.
- `AccountCacheServiceImpl`의 빈 이름은 명시 이름이 없으면 `accountCacheServiceImpl`이다.
- `LogicalExpirationCacheServiceImpl`은 warmUp이 필요한 전략이다. cold start 동작과 warm cache 동작을 구분해야 한다.
- Redis 직렬화 설정을 바꾸면 기존 캐시 값 역직렬화 테스트가 깨질 수 있다.
- `@TestPropertySource` 조합이 늘어나면 Testcontainers 컨텍스트가 분리되어 Redisson 연결 타이밍 이슈가 재발할 수 있다.
