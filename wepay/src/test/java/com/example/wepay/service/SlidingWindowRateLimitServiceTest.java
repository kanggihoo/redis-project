package com.example.wepay.service;

import com.example.wepay.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SlidingWindowRateLimitServiceTest {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("slidingWindow")
    RateLimitService slidingWindowService;

    @BeforeEach
    void clearRedis() {
        var keys = stringRedisTemplate.keys("ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("[Sliding Window] maxRequests 이내 호출은 허용, 초과 시 차단")
    void allowsUpToMaxRequests_thenBlocks() {
        for (int i = 0; i < 5; i++) {
            assertThat(slidingWindowService.isAllowed("transfer", "127.0.0.1"))
                    .as("요청 %d번째는 허용되어야 한다", i + 1)
                    .isTrue();
        }

        assertThat(slidingWindowService.isAllowed("transfer", "127.0.0.1"))
                .as("6번째 요청은 차단되어야 한다")
                .isFalse();
    }

    @Test
    @DisplayName("[Sliding Window] 경계 조건에서 Fixed Window와 달리 정확히 차단한다")
    void slidingWindow_blocksAtBoundary_unlikeFixedWindow() throws InterruptedException {
        // 직접 인스턴스 생성: maxRequests=3, windowSeconds=2
        SlidingWindowRateLimitService service =
                new SlidingWindowRateLimitService(stringRedisTemplate, 3, 2);

        // 3회 호출 → 모두 허용
        for (int i = 0; i < 3; i++) {
            assertThat(service.isAllowed("transfer", "boundary-user"))
                    .as("요청 %d번째는 허용", i + 1)
                    .isTrue();
        }

        // 1초 대기 (윈도우 만료 전)
        Thread.sleep(1000);

        // 4번째 호출 → Sliding Window는 차단해야 함 (이전 3개가 아직 윈도우 내)
        assertThat(service.isAllowed("transfer", "boundary-user"))
                .as("Sliding Window는 윈도우 내 요청이 maxRequests에 도달했으므로 차단")
                .isFalse();
    }
}
