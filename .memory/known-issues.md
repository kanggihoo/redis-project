# Known Issues

## Phase 2 K6 비교 결과 미기록

- 상태: open
- 내용: `load-tests/stampeding-herd.js`와 `docker-compose.yml`의 k6 profile은 존재하지만, Mutex Lock / Logical Expiration / TTL Jitter 비교 부하테스트 수치가 문서에 기록되지 않았다.
- 영향: Phase 2의 학습 목표 중 "전략별 비교 수치"가 아직 완결되지 않았다.
- 다음 행동: Phase 3 초반 또는 별도 검증 작업에서 K6를 실행하고 결과를 `docs/phase2-retrospective.md` 또는 별도 결과 문서에 기록한다.

## Testcontainers + Redisson 컨텍스트 분리 타이밍 이슈

- 상태: watch
- 내용: `@TestPropertySource` 조합이 다른 테스트 클래스가 새 ApplicationContext를 만들 때 Redisson이 이전 Testcontainer 포트를 참조하는 연결 실패가 발생한 적이 있다.
- 영향: 전체 테스트 실행 시 간헐 실패 가능성이 있다.
- 완화: 불필요한 `@TestPropertySource` 추가를 피하고 공통 테스트 설정은 `application-test.yml` 또는 `src/test/resources/application.yml`에 둔다. 필요 시 `./gradlew cleanTest` 후 재실행한다.

## Logical Expiration의 범용 래퍼 부재

- 상태: accepted
- 내용: 제네릭 `CacheWrapper<T>` 대신 `AccountCacheWrapper` 구체 클래스를 사용한다.
- 영향: 다른 도메인 객체에 Logical Expiration을 적용하려면 별도 래퍼 또는 커스텀 직렬화가 필요하다.
- 현재 판단: Phase 2 범위에서는 재사용성보다 역직렬화 안정성이 중요하다.

## README의 Next.js BFF는 아직 구현되지 않음

- 상태: open
- 내용: 장기 아키텍처에는 Next.js BFF가 포함되어 있지만 현재 확인된 구현은 Spring Boot API 중심이다.
- 영향: 프론트엔드/BFF 관련 작업을 시작할 때 신규 프로젝트 구조 결정이 필요하다.
- 다음 행동: BFF 작업 요청이 있을 때 별도 계획을 세운다.
