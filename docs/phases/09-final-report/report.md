# Report

이 문서는 기본 템플릿이다. Phase 성격에 따라 섹션을 추가하거나 삭제할 수 있다.

## Summary

Not measured yet.

## Redis Feature Decision Table

| Problem | Redis Feature | Benefit | Risk | Evidence |
|---|---|---|---|---|
| repeated reads | Cache Aside | | | |
| cache expiry burst | Mutex / logical expiration / jitter | | | |
| ranking | ZSET | | | |
| unique count | HyperLogLog | | | |
| atomic command bundle | Lua | | | |
| real-time notification | Pub/Sub | | | |
| durable event processing | Streams | | | |
| abuse traffic | Rate Limiting | | | |
