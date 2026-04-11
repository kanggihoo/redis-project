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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cache Invalidation — 트랜잭션 타이밍 버그 재현 및 해결 검증
 *
 * [버그 시나리오]
 *   1. updateBalance() 트랜잭션 시작
 *   2. DB UPDATE
 *   3. evictAccount() → 캐시 삭제  ← ⚠️ 버그: 커밋 전에 삭제
 *   4. 예외 발생 → 트랜잭션 롤백
 *   결과: DB는 원래 값으로 복구됐지만 캐시는 이미 삭제됨
 *         → 다음 조회 시 Cache Miss → DB에서 "롤백된 구버전 값" 캐싱
 *
 * [해결]
 *   @TransactionalEventListener(AFTER_COMMIT) 으로 캐시 삭제를 커밋 후로 지연
 *   → 롤백 시에는 캐시 삭제 자체가 발생하지 않음
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = "DELETE FROM accounts", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CacheInvalidationTest {

    @Autowired AccountBalanceService accountBalanceService;
    @Autowired AccountCacheService accountCacheService;
    @Autowired RedisTemplate<String, Object> redisTemplate;
    @Autowired StringRedisTemplate stringRedisTemplate;
    @Autowired PlatformTransactionManager txManager;

    @BeforeEach
    void clearCaches() {
        var keys = redisTemplate.keys("account:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        var nullKeys = stringRedisTemplate.keys("null:account:*");
        if (nullKeys != null && !nullKeys.isEmpty()) stringRedisTemplate.delete(nullKeys);
    }

    @Test
    @DisplayName("[BUG] 트랜잭션 롤백 시에도 캐시가 삭제된다 → 이후 조회에서 낡은 값이 캐싱된다")
    void bug_rollback_evictsCache() {
        Long accountId = 1L;
        Long originalBalance = 1_000_000L;

        // given: 계좌를 미리 캐싱 (잔액 1,000,000)
        Account original = accountCacheService.getAccount(accountId);
        assertThat(original.getBalance()).isEqualTo(originalBalance);
        assertThat(redisTemplate.opsForValue().get("account:" + accountId)).isNotNull();

        // when: updateBalance() → DB UPDATE + evict → 예외 발생 → 롤백
        // TransactionTemplate으로 rollback-only 트랜잭션을 명시적으로 실행
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        assertThatThrownBy(() -> txTemplate.execute(status -> {
            accountBalanceService.updateBalance(accountId, 9_999_999L);
            status.setRollbackOnly(); // 강제 롤백
            throw new RuntimeException("강제 롤백");
        })).isInstanceOf(RuntimeException.class);

        // then: DB는 롤백 → 여전히 1,000,000
        // but 버그: evictAccount()가 커밋 전에 실행됐으므로 캐시는 이미 삭제됨
        Object cachedAfterRollback = redisTemplate.opsForValue().get("account:" + accountId);

        // ✅ 수정 후: AFTER_COMMIT 이므로 롤백 시 캐시 삭제가 발생하지 않는다
        // 캐시는 롤백 전 값(1,000,000)이 그대로 보존되어야 한다
        assertThat(cachedAfterRollback).isNotNull();

        // 이후 조회: Cache Miss → DB 조회 → 롤백된 구버전(1,000,000) 재캐싱
        Account reloaded = accountCacheService.getAccount(accountId);
        assertThat(reloaded.getBalance()).isEqualTo(originalBalance);
        // 버그: 정상적으로 보이지만 캐시가 불필요하게 삭제된 것 — 커밋 전 evict가 원인
        // 멀티스레드 환경에서는 이 Window에서 다른 스레드가 구버전을 캐싱할 수 있음
    }

    @Test
    @DisplayName("[NORMAL] updateBalance() 커밋 성공 후 캐시가 새 잔액을 반환해야 한다")
    void normal_afterCommit_cacheShouldReflectNewBalance() {
        Long accountId = 1L;
        Long newBalance = 5_000_000L;

        accountCacheService.getAccount(accountId);
        accountBalanceService.updateBalance(accountId, newBalance);

        Account result = accountCacheService.getAccount(accountId);
        assertThat(result.getBalance()).isEqualTo(newBalance);
    }
}
