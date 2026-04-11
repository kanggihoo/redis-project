package com.example.wepay.service;

import com.example.wepay.domain.Account;
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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
        "cache.account.logical-ttl-seconds=2"
})
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = "DELETE FROM accounts", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class LogicalExpirationCacheServiceTest {

    @Autowired
    @Qualifier("logicalExpiration")
    LogicalExpirationCacheServiceImpl logicalExpirationCacheService;

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
    @DisplayName("[Logical Expiration A] 논리적 TTL 만료 후 20스레드 동시 조회 → DB 1회 + 모든 스레드 Account 반환")
    void logicalExpiration_afterLogicalTtlExpiry_dbCalledOnceAndAllThreadsGetAccount() throws Exception {
        int threadCount = 20;
        Long accountId = 1L;

        // given: warmUp 후 논리적 TTL 만료 대기
        logicalExpirationCacheService.warmUp(accountId);
        clearInvocations(accountRepository);
        TimeUnit.MILLISECONDS.sleep(2500); // 논리적 TTL 2초 만료

        // when: 20스레드 동시 조회
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Account> results = new CopyOnWriteArrayList<>();
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Account account = logicalExpirationCacheService.getAccount(accountId);
                    results.add(account);
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

        // 백그라운드 갱신 완료 대기
        TimeUnit.MILLISECONDS.sleep(500);

        // then: 모든 스레드가 Account를 반환 (stale 데이터라도)
        assertThat(results).hasSize(threadCount);
        assertThat(errorCount.get()).isZero();

        // DB 호출은 정확히 1회 (백그라운드 갱신)
        int dbCallCount = (int) mockingDetails(accountRepository)
                .getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("findById"))
                .count();

        assertThat(dbCallCount).isEqualTo(1);

        System.out.printf("""
                [Logical Expiration A 결과]
                  스레드 수: %d / 응답 수: %d / DB 호출: %d / 에러: %d
                %n""", threadCount, results.size(), dbCallCount, errorCount.get());
    }

    @Test
    @DisplayName("[Logical Expiration B] 논리적 TTL 미만료 → 조회 → DB 0회 (캐시 직접 반환)")
    void logicalExpiration_beforeLogicalTtlExpiry_dbNeverCalled() {
        Long accountId = 1L;

        // given: warmUp (TTL 유효)
        logicalExpirationCacheService.warmUp(accountId);
        clearInvocations(accountRepository);

        // when
        Account result = logicalExpirationCacheService.getAccount(accountId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOwnerName()).isEqualTo("Alice");

        int dbCallCount = (int) mockingDetails(accountRepository)
                .getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("findById"))
                .count();

        assertThat(dbCallCount).isZero();
    }
}
