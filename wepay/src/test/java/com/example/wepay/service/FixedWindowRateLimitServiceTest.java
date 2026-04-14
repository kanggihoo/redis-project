package com.example.wepay.service;

import com.example.wepay.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FixedWindowRateLimitServiceTest {

    @Autowired
    @Qualifier("fixedWindow")
    RateLimitService rateLimitService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearRedis() {
        var keys = stringRedisTemplate.keys("ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("[Fixed Window] maxRequests 이내 호출은 허용, 초과 시 차단")
    void allowsUpToMaxRequests_thenBlocks() {
        // 5회 호출 → 모두 허용
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimitService.isAllowed("transfer", "127.0.0.1"))
                    .as("요청 %d번째는 허용되어야 한다", i + 1)
                    .isTrue();
        }

        // 6번째 호출 → 차단
        assertThat(rateLimitService.isAllowed("transfer", "127.0.0.1"))
                .as("6번째 요청은 차단되어야 한다")
                .isFalse();
    }
}
