# Decisions

## 도메인 객체 캐시는 RedisTemplate, 문자열 값은 StringRedisTemplate 사용

- 날짜: 2026-05-08
- 상태: active
- 결정: `Account` 같은 도메인 객체 캐싱에는 `RedisTemplate<String, Object>`를 사용하고, 카운터/마커/ZSET/HLL/Geo 멤버처럼 값이 문자열인 경우에는 `StringRedisTemplate`을 사용한다.
- 이유: JSON 직렬화가 필요한 객체와 Redis CLI에서 바로 읽히는 문자열 자료구조의 요구가 다르다. 문자열 값에 객체 템플릿을 쓰면 직렬화 오버헤드와 가독성 문제가 생긴다.
- 버린 대안: 모든 Redis 접근을 `RedisTemplate<String, Object>`로 통일.
- 재검토 조건: Redis 직렬화 정책을 전역적으로 변경하거나, 문자열 자료구조에 복합 객체를 저장해야 할 때.

## 캐시 무효화는 커밋 이후 이벤트 리스너에서 수행

- 날짜: 2026-05-08
- 상태: active
- 결정: 잔액 변경 후 캐시 삭제는 `AccountCacheEvictEvent`를 발행하고 `@TransactionalEventListener(AFTER_COMMIT)`에서 수행한다.
- 이유: 트랜잭션 내부에서 즉시 캐시를 삭제하면 롤백 시 DB는 복구되지만 캐시는 이미 삭제되어 정합성 문제가 생긴다.
- 버린 대안: 서비스 메서드 안에서 `evictAccount()` 직접 호출.
- 재검토 조건: 캐시 무효화 정책이 outbox/event broker 기반으로 확장될 때.

## Logical Expiration은 AccountCacheWrapper 구체 클래스를 사용

- 날짜: 2026-05-08
- 상태: active
- 결정: Logical Expiration 캐시 값은 제네릭 `CacheWrapper<T>`가 아니라 `AccountCacheWrapper` 구체 클래스를 사용한다.
- 이유: `GenericJacksonJsonRedisSerializer`와 제네릭 record 조합에서 타입 소거로 역직렬화 문제가 발생했다.
- 버린 대안: 제네릭 record 유지, 커스텀 Jackson TypeReference 기반 직렬화.
- 재검토 조건: 여러 도메인 객체에 Logical Expiration을 공통 적용해야 할 때.

## warmUp은 AccountCacheService 공통 인터페이스에 넣지 않는다

- 날짜: 2026-05-08
- 상태: active
- 결정: `warmUp(Long id)`는 Logical Expiration 구현체 전용 메서드로 둔다.
- 이유: warmUp은 Logical Expiration 전략에만 필요한 개념이며, 공통 인터페이스에 넣으면 다른 구현체가 의미 없는 메서드를 가져야 한다.
- 버린 대안: `AccountCacheService`에 `warmUp()` 추가.
- 재검토 조건: 컨트롤러나 운영 API에서 모든 캐시 전략의 사전 적재를 공통으로 다뤄야 할 때.

## 테스트 시간 의존성은 가능하면 Clock으로 격리

- 날짜: 2026-05-08
- 상태: active
- 결정: Phase 3의 Rate Limiting처럼 시간 경계가 핵심인 테스트는 실제 `Thread.sleep()` 대신 `Clock` 추상화를 우선 검토한다.
- 이유: Fixed Window 경계 조건과 Sliding Window 검증은 실제 시간에 의존하면 느리고 불안정하다.
- 버린 대안: sleep으로 59초/61초 경계를 직접 기다리는 테스트.
- 재검토 조건: Redis TTL 자체의 실제 만료 동작을 검증해야 하는 통합 테스트일 때.

## Geo 테스트는 경계에 가까운 좌표/반경을 피한다

- 날짜: 2026-05-08
- 상태: active
- 결정: Geospatial 테스트 데이터는 포함/제외가 명확한 거리 차이를 갖게 만든다.
- 이유: Redis GEO는 Geohash 인코딩과 부동소수점 오차가 있어 경계 근처 결과가 불안정할 수 있다.
- 버린 대안: 계획서의 5km 반경 값을 실제 거리 검산 없이 사용.
- 재검토 조건: 경계 포함 정책 자체를 테스트해야 할 때.
