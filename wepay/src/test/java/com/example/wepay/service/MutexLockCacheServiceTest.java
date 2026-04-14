package com.example.wepay.service;

import com.example.wepay.TestcontainersConfiguration;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "cache.account.ttl-seconds=2")
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = "DELETE FROM accounts", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class MutexLockCacheServiceTest {

    @Autowired
    @Qualifier("mutexLock")
    AccountCacheService mutexLockCacheService;

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @MockitoSpyBean
    AccountRepository accountRepository;

    @BeforeEach
    void clearCache() {
        var keys = redisTemplate.keys("account:*");
        if (keys != null && !keys.isEmpty())
            redisTemplate.delete(keys);
        var lockKeys = redisTemplate.keys("lock:account:*");
        if (lockKeys != null && !lockKeys.isEmpty())
            redisTemplate.delete(lockKeys);
        clearInvocations(accountRepository);
    }

    @Test
    @DisplayName("[Mutex Lock] TTL 만료 후 20스레드 동시 조회 → DB findById 정확히 1회만 호출")
    void mutexLock_afterTtlExpiry_dbCalledExactlyOnce() throws Exception {
        int threadCount = 20;
        Long accountId = 1L;

        // given: 캐싱 후 TTL 만료 대기
        mutexLockCacheService.getAccount(accountId);
        clearInvocations(accountRepository);
        TimeUnit.MILLISECONDS.sleep(2500); // TTL 2초 만료

        assertThat(redisTemplate.opsForValue().get(AccountCacheService.CACHE_PREFIX + accountId)).isNull();

        // when: 20스레드 동시 조회
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    mutexLockCacheService.getAccount(accountId);
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

        // then: DB 호출 정확히 1회
        int dbCallCount = (int) mockingDetails(accountRepository)
                .getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("findById"))
                .count();

        assertThat(dbCallCount).isEqualTo(1);
        assertThat(errorCount.get()).isZero();

        System.out.printf("""
                [Mutex Lock 결과]
                  스레드 수: %d
                  DB 호출 수: %d  ← Mutex Lock으로 1회만 호출됨
                  에러 수: %d
                %n""", threadCount, dbCallCount, errorCount.get());
    }
}
