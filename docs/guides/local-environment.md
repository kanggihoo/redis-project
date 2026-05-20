# Local Environment Guide

Redis 실험을 반복 실행하기 위한 로컬 환경 가이드다.

## Services

- Spring Boot Core API: `localhost:8080`
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`
- Prometheus: `localhost:9090`
- Grafana: `localhost:3000`

## Common Flow

1. Docker Compose로 PostgreSQL, Redis, monitoring stack을 실행한다.
2. Spring Boot 애플리케이션을 실행한다.
3. 테스트 데이터를 초기화한다.
4. k6 또는 JUnit/Testcontainers 시나리오를 실행한다.
5. Redis/DB/Grafana evidence를 저장한다.
