# Conventions

## 작업 방식

- Phase 작업은 문제 재현 또는 한계 증명 테스트를 먼저 만들고, Redis 기능으로 해결한다.
- TDD 흐름은 `껍데기 -> Red -> Green -> 리팩토링`을 따른다.
- import/컴파일 에러만 나는 상태를 의미 있는 Red로 보지 않는다.
- 구현 후에는 변경 범위에 맞는 테스트를 실행한다.
- 세션 시작 시에는 테스트를 실행하지 않는다.

## PowerShell/Encoding

- 한글 문서를 읽을 때는 UTF-8을 명시한다.
  - `Get-Content -Encoding UTF8 path\to\file.md`
- 파일 검색은 우선 `rg`를 사용한다.

## Spring Boot 4 테스트 규칙

- Spring Boot 4에서는 기존 `@MockBean`, `@SpyBean` 대신 Spring Test의 Mockito Bean Override API를 사용한다.
  - `@MockitoBean`
  - `@MockitoSpyBean`
- MVC 슬라이스 테스트의 `@WebMvcTest`는 Spring Boot 4 패키지를 확인한다.
  - `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
- 컨트롤러 테스트는 가능하면 `@WebMvcTest` + `MockMvcTester`로 작성한다.
- 서비스/Redis/JPA 통합 테스트는 `@SpringBootTest` + Testcontainers를 사용한다.
- 같은 타입 빈이 여러 개면 `@MockitoBean(name = "...")`로 빈 이름을 명시한다.

## Redis 사용 규칙

- 객체 캐싱: `RedisTemplate<String, Object>`
- 문자열/카운터/마커/ZSET/HLL/Geo: `StringRedisTemplate`
- 키 네이밍은 도메인과 자료구조 목적이 드러나야 한다.

현재 주요 키:

```text
account:{id}             -> Account JSON / TTL
null:account:{id}        -> NULL marker / TTL 30s
store:txcount:{storeId}  -> write-back counter
lock:account:{id}        -> Redisson lock
store:ranking            -> ZSET
dau:{yyyy-MM-dd}         -> HyperLogLog
store:locations          -> Geo
```

## 설정

- 계좌 캐시 TTL: `cache.account.ttl-seconds`
- 기본 로컬 인프라:
  - PostgreSQL: `localhost:5432`
  - Redis: `localhost:6379`
  - Prometheus: `localhost:9090`
  - Grafana: `localhost:3000`

## 문서화

- 완료 이력을 길게 메모리에 복사하지 않는다.
- 장기 판단에 필요한 설계 결정은 `decisions.md`에 남긴다.
- 반복 오류와 검증된 해결 절차는 `troubleshooting.md`에 남긴다.
- 현재 작업 큐와 다음 진입점은 `current-state.md`, `tasks.md`에 짧게 유지한다.
