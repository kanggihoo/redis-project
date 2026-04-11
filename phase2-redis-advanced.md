# Redis Phase 2: Stampeding Herd 해결 및 특수 자료구조 적용

## Goal
캐시 만료 시 발생하는 Stampeding Herd 문제를 Redisson Mutex Lock으로 해결하고, Redis의 특수 자료구조(ZSET, HyperLogLog, Geospatial)를 실무 도메인에 적용합니다.

## Tasks
- [ ] Task 1: Redisson 의존성 추가 및 설정 → Verify: `RedissonClient` 빈 등록 및 연결 확인
- [ ] Task 2: Stampeding Herd 방어 (Mutex Lock) 구현 → Verify: k6 부하 테스트로 DB 쿼리가 1건만 발생하는지 그래프/로그 환인
- [ ] Task 3: 실시간 랭킹 (ZSET) 구현 → Verify: `ZINCRBY`, `ZREVRANGE` 기반 Top 10 가맹점 API가 정상 동작하는지 테스트
- [ ] Task 4: DAU 분석 (HyperLogLog) 구현 → Verify: `PFADD`, `PFCOUNT` 기반 중복 없는 사용자 카운트 API 확인
- [ ] Task 5: 주변 상권 검색 (Geospatial) 구현 → Verify: `GEOADD`, `GEORADIUS` 기반 위치 반경 검색 API 확인

## Done When
- [ ] Redisson 분산 락을 통해 캐시 폭증 시 DB를 보호할 수 있다.
- [ ] k6를 통한 부하 테스트로 방어 기법의 효과를 수치로 증명할 수 있다.
- [ ] ZSET, HyperLogLog, Geospatial을 활용한 신규 API 3종이 정상 응답한다.
