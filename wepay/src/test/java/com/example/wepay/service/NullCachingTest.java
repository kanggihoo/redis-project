package com.example.wepay.service;
import com.example.wepay.TestcontainersConfiguration;

import com.example.wepay.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NullCachingTest {

    @Autowired
    AccountCacheService accountCacheService;

    @MockitoSpyBean
    AccountRepository accountRepository;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void clearCaches() {
        // account:* 와 null:account:* 키 모두 초기화
        var accountKeys = redisTemplate.keys("account:*");
        if (accountKeys != null && !accountKeys.isEmpty()) redisTemplate.delete(accountKeys);

        var nullKeys = stringRedisTemplate.keys("null:account:*");
        if (nullKeys != null && !nullKeys.isEmpty()) stringRedisTemplate.delete(nullKeys);
    }

    @Test
    @DisplayName("[Null Cache Hit] 존재하지 않는 계좌를 2회 조회해도 DB는 1회만 호출된다")
    void getAccount_nonExistent_dbCalledOnce() {
        Long nonExistentId = 9999L;

        // 첫 번째 조회 → DB Miss → Null 마커 캐싱 → 예외
        assertThatThrownBy(() -> accountCacheService.getAccount(nonExistentId))
                .isInstanceOf(IllegalArgumentException.class);

        // 두 번째 조회 → Null 캐시 히트 → DB 미호출 → 예외
        assertThatThrownBy(() -> accountCacheService.getAccount(nonExistentId))
                .isInstanceOf(IllegalArgumentException.class);

        // DB(findById)는 단 1회만 호출되어야 한다
        verify(accountRepository, times(1)).findById(nonExistentId);
    }

    @Test
    @DisplayName("[Null Cache Invalidation] 계좌 생성 후 evictAccount() 호출 시 Null 캐시가 삭제된다")
    void evictAccount_removesNullCache() {
        Long id = 9999L;

        // given: Null 마커를 수동으로 심어둔다
        stringRedisTemplate.opsForValue().set("null:account:" + id, "NULL");

        // when: evict 호출 (새 계좌 생성 후 캐시 무효화 시나리오)
        accountCacheService.evictAccount(id);

        // then: Null 캐시 키가 삭제되어야 한다
        String nullMarker = stringRedisTemplate.opsForValue().get("null:account:" + id);
        assertThat(nullMarker).isNull();
    }
}
