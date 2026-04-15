 
 ## SlidingWindowRateLimitService의
 Redis 원자성(Atomicity) 개선 (Lua Script)
  특히 SlidingWindowRateLimitService의 경우 removeRangeByScore, zCard, add가 각각 별도의 Redis 호출로 이루어져 있어 경합
  조건(Race Condition)이 발생할 수 있습니다. 이를 Lua 스크립트로 묶어 원자성을 보장하는 것이 좋습니다.

### 3. Redis에서 키 설정과 TTL을 한 번에 할 수 없는가? (질문 2 답변)

**결론부터 말씀드리면, `INCR` 명령어는 TTL 설정을 동시에 지원하지 않습니다.**

*   **`SET` 명령어의 경우:** `SET key value EX 10`처럼 값 설정과 만료 시간 설정을 동시에(Atomic하게) 할 수 있습니다.
*   **`INCR` 명령어의 경우:** 아쉽게도 Redis 자체 명령어 수준에서 `INCR`과 `EXPIRE`를 동시에 처리하는 단일 명령은 없습니다.

**현재 코드의 잠재적 문제점 (Race Condition):**
위 코드에서 `increment()`는 성공했는데, 바로 다음 줄인 `expire()`가 실행되기 직전에 애플리케이션이 종료되거나 네트워크 장애가 발생하면 어떻게 될까요? 
- 해당 키는 **TTL이 설정되지 않은 채 Redis에 영원히 남게 됩니다.**
- 이 경우 해당 사용자는 영구적으로 차단(Rate Limit에 걸림)될 위험이 있습니다.

**권장되는 해결책:**
이런 원자성(Atomicity) 문제를 해결하기 위해 실제 운영 환경에서는 보통 **Lua Script**를 사용합니다.

```lua
-- Lua Script 예시
local current = redis.call("INCR", KEYS[1])
if current == 1 then
    redis.call("EXPIRE", KEYS[1], ARGV[1])
end
return current
```
이렇게 Lua 스크립트를 사용하면 Redis 서버 내부에서 두 명령어가 하나의 트랜잭션처럼 묶여서 실행되므로 훨씬 안전합니다.
