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

/**
 * Fixed Window 경계 조건 버그 증명 테스트.
 * 윈도우 경계에서 maxRequests × 2 만큼의 요청이 짧은 시간 내에 통과될 수 있음을 증명한다.
 *
 * 이것은 "통과하면 버그가 있다"는 것을 증명하는 테스트이다.
 */
@SpringBootTest(properties = {
        "rate-limit.transfer.max-requests=3",
        "rate-limit.transfer.window-seconds=2"
})
@Import(TestcontainersConfiguration.class)
class FixedWindowBoundaryBugTest {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    FixedWindowRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        var keys = stringRedisTemplate.keys("ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        // 테스트용: maxRequests=3, windowSeconds=2
        rateLimitService = new FixedWindowRateLimitService(stringRedisTemplate, 3, 2);
    }

    @Test
    @DisplayName("[Fixed Window 버그] 윈도우 경계에서 2×maxRequests 요청이 통과된다")
    void boundaryBug_allowsDoubleMaxRequestsAcrossWindowBoundary() throws InterruptedException {
        // 첫 번째 윈도우: 3회 호출 → 모두 허용
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimitService.isAllowed("transfer", "user1"))
                    .as("첫 번째 윈도우 %d번째 요청은 허용", i + 1)
                    .isTrue();
        }

        // 윈도우 만료 대기 (2.5초)
        Thread.sleep(2500);

        // 두 번째 윈도우: 즉시 3회 호출 → 모두 허용
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimitService.isAllowed("transfer", "user1"))
                    .as("두 번째 윈도우 %d번째 요청은 허용", i + 1)
                    .isTrue();
        }

        // 결과: 실질적으로 ~2.5초 내에 6회(2×maxRequests) 요청이 통과됨
        // 이것이 Fixed Window의 근본적 한계 — Sliding Window로 해결 필요
    }
}
