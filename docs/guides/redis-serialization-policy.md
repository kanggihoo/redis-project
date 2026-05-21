# Redis Serialization Policy

Serialization은 별도 Phase로 깊게 다루지 않고 기본 정책으로 관리한다.

## Defaults

- counters, flags, locks: String
- numeric values: String value with explicit parsing
- simple cached DTO: JSON
- JDK serialization: avoid

## Recording

객체 캐시를 사용하는 Phase에서는 payload 크기와 memory usage를 report에 기록한다.
