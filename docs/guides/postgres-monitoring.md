# PostgreSQL Monitoring Guide

PostgreSQL은 Redis 적용 전후 DB 부하가 줄었는지 확인하기 위한 보조 관측 대상이다.

## Useful Signals

- query count
- slow query
- connection usage
- target API별 DB access count

## Note

이 프로젝트의 주인공은 Redis다. PostgreSQL 관측은 Redis 적용 효과를 설명하는 보조 지표로 사용한다.
