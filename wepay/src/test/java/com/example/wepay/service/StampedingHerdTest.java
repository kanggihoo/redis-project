package com.example.wepay.service;

import com.example.wepay.repository.AccountRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

/**
 * Stampeding Herd (Cache Stampede) 현상 재현
 *
 * 현상:
 *   TTL 만료 직후 다수 요청이 동시에 Cache Miss → 모두 DB 조회 → DB 쿼리 폭증
 *   100만 사용자 서비스에서 인기 키 하나의 TTL이 만료되면 수천 건의 DB 쿼리가 동시 발생
 *
 * 재현:
 *   TTL 2초 설정 → 캐싱 → TTL 만료까지 대기 → 20스레드 동시 getAccount() 호출
 *   → DB(findById) 20회 호출 확인
 *
 * 해결책 (Phase 2에서 구현):
 *   1. Mutex Lock (분산 락): 첫 번째 스레드만 DB 조회, 나머지는 대기 → 1회 호출
 *   2. Logical Expiration: 실제 TTL 없이 만료 시간을 값에 포함 → 백그라운드 갱신
 */
@SpringBootTest
@Import(com.example.wepay.TestcontainersConfiguration.class)
@TestPropertySource(properties = "cache.account.ttl-seconds=2")
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = "DELETE FROM accounts", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class StampedingHerdTest {

    @Autowired AccountCacheService accountCacheService;
    @Autowired RedisTemplate<String, Object> redisTemplate;
    @Autowired StringRedisTemplate stringRedisTemplate;

    @MockitoSpyBean
    AccountRepository accountRepository;

    @BeforeEach
    void clearCaches() {
        var keys = redisTemplate.keys("account:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        var nullKeys = stringRedisTemplate.keys("null:account:*");
        if (nullKeys != null && !nullKeys.isEmpty()) stringRedisTemplate.delete(nullKeys);
    }

    @Test
    @DisplayName("[Stampeding Herd] TTL 만료 직후 20스레드 동시 조회 → DB 쿼리 20회 폭증")
    void stampedingHerd_afterTtlExpiry_dbCalledByAllThreads() throws Exception {
        int threadCount = 20;
        Long accountId = 1L;

        // given: 캐싱 후 TTL 만료 대기
        accountCacheService.getAccount(accountId);
        TimeUnit.MILLISECONDS.sleep(2500); // TTL 2초 만료

        // 캐시가 만료되었는지 확인
        assertThat(redisTemplate.opsForValue().get("account:" + accountId)).isNull();

        // when: 20 스레드가 동시에 Cache Miss → DB 조회
        CountDownLatch startLatch = new CountDownLatch(1);  // 동시 출발 신호
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 동시에 출발
                    accountCacheService.getAccount(accountId);
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 동시 출발
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then: DB(findById)가 여러 번 호출됨 (Stampeding Herd 확인)
        int dbCallCount = mockingDetails(accountRepository)
                .getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("findById"))
                .mapToInt(inv -> 1)
                .sum();

        // ⚠️ Stampeding Herd: 여러 스레드가 동시에 DB를 조회했음을 확인
        // 이상적으로는 1회만 호출되어야 하지만, 동기화 없이는 N회 호출됨
        assertThat(dbCallCount).isGreaterThan(1);

        System.out.printf("""
                [Stampeding Herd 결과]
                  스레드 수: %d
                  DB 호출 수: %d  ← 이상적으로는 1회여야 함
                  에러 수: %d

                  Phase 2에서 Mutex Lock / Logical Expiration 으로 DB 호출을 1회로 줄인다.
                %n""", threadCount, dbCallCount, errorCount.get());
    }

    @Test
    @DisplayName("[기준선] 캐시가 살아있을 때 20스레드 조회 → DB는 0회 호출된다")
    void baseline_cacheAlive_dbNeverCalled() throws Exception {
        int threadCount = 20;
        Long accountId = 1L;

        // given: 캐싱 (TTL 유효)
        accountCacheService.getAccount(accountId);
        assertThat(redisTemplate.opsForValue().get("account:" + accountId)).isNotNull();

        // Spy 초기화 (위의 getAccount 1회 호출 제외)
        org.mockito.Mockito.clearInvocations(accountRepository);

        // when: 20 스레드 동시 조회 (캐시 Hit)
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    accountCacheService.getAccount(accountId);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then: 캐시 Hit → DB 호출 0회
        int dbCallCount = mockingDetails(accountRepository)
                .getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("findById"))
                .mapToInt(inv -> 1)
                .sum();

        assertThat(dbCallCount).isZero();

        System.out.printf("""
                [기준선 결과]
                  스레드 수: %d
                  DB 호출 수: %d  ← 캐시 Hit으로 DB 미호출
                %n""", threadCount, dbCallCount);
    }
}
