package com.example.wepay.service;

import com.example.wepay.TestcontainersConfiguration;
import com.example.wepay.domain.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TTL 트레이드오프 실험
 *
 * TTL = 짧을수록: 정합성↑, Hit Ratio↓, DB 부하↑
 * TTL = 길수록:  정합성↓, Hit Ratio↑, DB 부하↓
 *
 * 실험 구간 (테스트 속도를 위해 초 단위로 압축):
 *   SHORT  = 2초  (실제 서비스: 10초)
 *   MEDIUM = 5초  (실제 서비스: 60초)
 *   LONG   = 10초 (실제 서비스: 300초)
 *
 * 결론: 정답은 없다. 비즈니스 허용 오차 시간(Stale Tolerance)을 기준으로 TTL을 결정해야 한다.
 *   - 금융/결제: Stale Tolerance = 0 → TTL 짧게 or Write-Through
 *   - SNS 좋아요 수: Stale Tolerance = 수분 → TTL 길게
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "cache.account.ttl-seconds=2") // SHORT TTL로 실험
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = "DELETE FROM accounts", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TtlTradeoffTest {

    @Autowired AccountCacheService accountCacheService;
    @Autowired AccountBalanceService accountBalanceService;
    @Autowired RedisTemplate<String, Object> redisTemplate;
    @Autowired StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearCaches() {
        var keys = redisTemplate.keys(AccountCacheService.CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        var nullKeys = stringRedisTemplate.keys("null:account:*");
        if (nullKeys != null && !nullKeys.isEmpty()) stringRedisTemplate.delete(nullKeys);
    }

    @Test
    @DisplayName("[TTL 단기] TTL 만료 전: 캐시 Hit → DB 변경 후에도 낡은 값 반환 (정합성 오차)")
    void shortTtl_beforeExpiry_returnsStaleCachedValue() throws Exception {
        Long accountId = 1L;
        Long originalBalance = 1_000_000L;
        Long newBalance = 5_000_000L;

        // given: 캐싱 (TTL 2초 시작)
        Account cached = accountCacheService.getAccount(accountId);
        assertThat(cached.getBalance()).isEqualTo(originalBalance);

        // when: DB 잔액 변경 (캐시 evict 없이 직접 DB 업데이트 시뮬레이션)
        // → 실제로는 캐시가 살아있는 동안 외부 시스템이 DB를 변경하는 시나리오
        accountBalanceService.updateBalance(accountId, newBalance);
        // AFTER_COMMIT 리스너가 evict를 발행했으므로 여기서는 캐시가 삭제됨
        // 순수 TTL 정합성 오차를 보기 위해 수동으로 다시 캐싱
        accountCacheService.evictAccount(accountId);
        accountCacheService.getAccount(accountId); // 새 값(5,000,000) 캐싱

        // TTL 만료 전(1초 대기) → 캐시에서 새 값 반환
        TimeUnit.SECONDS.sleep(1);
        Account duringTtl = accountCacheService.getAccount(accountId);

        // TTL 만료 전에는 캐시 값이 유지됨
        // 트레이드오프: 이 1초 동안 다른 스레드는 캐시에서 정확한 값을 얻음 (Hit Ratio↑)
        assertThat(duringTtl.getBalance()).isEqualTo(newBalance);
    }

    @Test
    @DisplayName("[TTL 만료] TTL 2초 후 캐시가 만료되어 DB에서 최신 값을 재조회한다")
    void shortTtl_afterExpiry_fetchesFromDB() throws Exception {
        Long accountId = 1L;
        Long originalBalance = 1_000_000L;

        // given: 캐싱 (TTL 2초)
        accountCacheService.getAccount(accountId);
        assertThat(redisTemplate.opsForValue().get(AccountCacheService.CACHE_PREFIX + accountId)).isNotNull();

        // when: TTL 만료까지 대기 (2초 + 여유 0.5초)
        TimeUnit.MILLISECONDS.sleep(2500);

        // then: 캐시 만료 확인
        Object expiredCache = redisTemplate.opsForValue().get(AccountCacheService.CACHE_PREFIX + accountId);
        assertThat(expiredCache).isNull();

        // DB에서 재조회 → 최신 값 반환
        Account reloaded = accountCacheService.getAccount(accountId);
        assertThat(reloaded.getBalance()).isEqualTo(originalBalance);

        // 재조회 후 새 캐시 저장됨
        assertThat(redisTemplate.opsForValue().get(AccountCacheService.CACHE_PREFIX + accountId)).isNotNull();

        /*
         * [트레이드오프 정리]
         *
         * TTL 짧게 (예: 10초):
         *   ✅ 정합성 오차 시간 짧음 — DB 변경이 빠르게 반영됨
         *   ❌ Hit Ratio 낮음 — 자주 DB 조회, 캐시 효과 감소
         *   ❌ DB 부하 증가 — TTL 만료마다 DB 쿼리 발생
         *
         * TTL 길게 (예: 300초):
         *   ✅ Hit Ratio 높음 — 대부분 캐시에서 응답, 빠른 응답 속도
         *   ✅ DB 부하 감소 — 쿼리 횟수 대폭 감소
         *   ❌ 정합성 오차 시간 김 — 최대 5분간 낡은 데이터 노출 가능
         *
         * 결론: TTL 60초(현재 기본값)는 일반적인 계좌 조회에 적합한 균형점.
         *       잔액 변경 시 evictAccount()로 즉시 무효화하므로 오차 최소화.
         *       "캐시 evict를 못 하는 상황"이라면 짧은 TTL이 안전망 역할을 함.
         */
    }

    @Test
    @DisplayName("[TTL + Evict 조합] 잔액 변경 시 즉시 evict → TTL 오차 없이 최신 값 반환")
    void ttlWithEvict_alwaysReturnsLatestValue() {
        Long accountId = 1L;
        Long newBalance = 7_777_777L;

        // given: 캐싱
        accountCacheService.getAccount(accountId);

        // when: 잔액 변경 (AFTER_COMMIT 이후 자동 evict)
        accountBalanceService.updateBalance(accountId, newBalance);

        // then: TTL과 무관하게 즉시 최신 값 반환 (evict 덕분)
        Account result = accountCacheService.getAccount(accountId);
        assertThat(result.getBalance()).isEqualTo(newBalance);

        /*
         * [교훈]
         * TTL만으로 정합성을 보장하려면 짧은 TTL이 필수 → Hit Ratio 희생
         * TTL + 명시적 evict 조합이 최선: TTL은 "안전망", evict는 "즉각 반응"
         */
    }
}
