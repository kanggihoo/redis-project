package com.example.wepay.service;

import com.example.wepay.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;

@SpringBootTest
@Import(com.example.wepay.TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "cache.account.ttl-seconds=60",
        "cache.account.jitter-max-seconds=30"
})
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = "DELETE FROM accounts", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TtlJitterCacheServiceTest {

    @Autowired
    @Qualifier("ttlJitter")
    AccountCacheService ttlJitterCacheService;

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @MockitoSpyBean
    AccountRepository accountRepository;

    @BeforeEach
    void clearCache() {
        var keys = redisTemplate.keys("account:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        clearInvocations(accountRepository);
    }

    @Test
    @DisplayName("[TTL Jitter A] 같은 키를 100번 캐싱하면 TTL 값이 모두 동일하지 않다 (Jitter 확인)")
    void ttlJitter_multipleCache_ttlValuesAreDifferent() {
        Long accountId = 1L;
        Set<Long> observedTtls = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            ttlJitterCacheService.evictAccount(accountId);
            ttlJitterCacheService.getAccount(accountId);
            Long ttl = redisTemplate.getExpire("account:" + accountId, TimeUnit.SECONDS);
            if (ttl != null && ttl > 0) {
                observedTtls.add(ttl);
            }
        }

        // Jitter가 적용되어 TTL이 다양한 값을 가져야 한다 (30초 범위 중 최소 2종류 이상)
        assertThat(observedTtls.size()).isGreaterThan(1);
        System.out.printf("[TTL Jitter A] 관찰된 TTL 종류 수: %d%n", observedTtls.size());
    }

    @Test
    @DisplayName("[TTL Jitter B] 단일 키 20스레드 동시 조회 → DB 호출 1회 초과 (단일 키 Stampede 해결 못함을 증명)")
    void ttlJitter_singleKeyStampede_dbCalledMoreThanOnce() throws Exception {
        int threadCount = 20;
        Long accountId = 1L;

        // given: 캐싱 후 만료 대기
        ttlJitterCacheService.getAccount(accountId);
        clearInvocations(accountRepository);
        // TTL 만료를 강제하기 위해 캐시 직접 삭제 (Jitter로 TTL이 60~90초라 대기 불가)
        redisTemplate.delete("account:" + accountId);

        // when: 20스레드 동시 조회
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ttlJitterCacheService.getAccount(accountId);
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // then: DB 호출이 1회 초과 (단일 키 Stampede 해결 못함)
        int dbCallCount = (int) mockingDetails(accountRepository)
                .getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("findById"))
                .count();

        assertThat(dbCallCount).isGreaterThan(1);
        System.out.printf("""
                [TTL Jitter B 결과]
                  스레드 수: %d / DB 호출: %d / 에러: %d
                  ⚠️ TTL Jitter는 단일 핫 키 Stampede를 해결하지 못함
                %n""", threadCount, dbCallCount, errorCount.get());
    }
}
