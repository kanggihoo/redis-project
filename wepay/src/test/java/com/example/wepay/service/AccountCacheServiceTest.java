package com.example.wepay.service;

import com.example.wepay.domain.Account;
import com.example.wepay.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(com.example.wepay.TestcontainersConfiguration.class)
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = "DELETE FROM accounts", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AccountCacheServiceTest {

    @Autowired
    AccountCacheService accountCacheService;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void clearRedis() {
        var keys = redisTemplate.keys("account:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("[Cache Hit] Redis에 캐시가 있으면 DB를 조회하지 않는다")
    void getAccount_cacheHit_doesNotQueryDB() {
        // given: Redis에 미리 캐시 저장
        Long id = 1L;
        Account cached = new Account("CachedAlice", 9_999_999L);
        redisTemplate.opsForValue().set(AccountCacheService.CACHE_PREFIX + id, cached);

        // when
        Account result = accountCacheService.getAccount(id);

        // then: DB 값(Alice / 1,000,000)이 아닌 캐시 값이 반환되어야 한다
        assertThat(result.getOwnerName()).isEqualTo("CachedAlice");
        assertThat(result.getBalance()).isEqualTo(9_999_999L);
    }

    @Test
    @DisplayName("[Cache Miss] 캐시가 없으면 DB에서 조회 후 Redis에 저장한다")
    void getAccount_cacheMiss_queriesDBAndCachesResult() {
        // given: Redis 비어있음 (BeforeEach에서 클리어)
        Long id = 1L;

        // when
        Account result = accountCacheService.getAccount(id);

        // then: DB에서 올바른 값 반환
        assertThat(result.getOwnerName()).isEqualTo("Alice");
        assertThat(result.getBalance()).isEqualTo(1_000_000L);

        // and: Redis에 캐시가 저장되었는지 확인
        Object cached = redisTemplate.opsForValue().get(AccountCacheService.CACHE_PREFIX + id);
        assertThat(cached).isNotNull();
    }
}
