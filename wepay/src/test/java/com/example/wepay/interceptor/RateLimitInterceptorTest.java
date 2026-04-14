package com.example.wepay.interceptor;

import com.example.wepay.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitInterceptorTest {

    @Autowired
    MockMvcTester mvc;

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
    @DisplayName("[Rate Limit HTTP] 송금 API 5회 → 200 OK, 6번째 → 429 Too Many Requests")
    void transferApi_blockedAfterMaxRequests() {
        for (int i = 0; i < 5; i++) {
            assertThat(mvc.post().uri("/api/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":10000}"))
                    .hasStatus(HttpStatus.OK);
        }

        // 6번째 요청 → 429
        assertThat(mvc.post().uri("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000}"))
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("[Rate Limit HTTP] 서로 다른 identifier는 독립적인 Rate Limit 적용")
    void differentIdentifiers_haveIndependentRateLimits() {
        // 기본 identifier(127.0.0.1)로 5회 소진
        for (int i = 0; i < 5; i++) {
            assertThat(mvc.post().uri("/api/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":10000}"))
                    .hasStatus(HttpStatus.OK);
        }

        // 6번째 → 429
        assertThat(mvc.post().uri("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000}"))
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }
}
