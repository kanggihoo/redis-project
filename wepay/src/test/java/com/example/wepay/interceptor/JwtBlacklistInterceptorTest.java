package com.example.wepay.interceptor;

import com.example.wepay.TestcontainersConfiguration;
import com.example.wepay.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JwtBlacklistInterceptorTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtTokenService jwtTokenService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearRedis() {
        var keys = stringRedisTemplate.keys("blacklist:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        var rateLimitKeys = stringRedisTemplate.keys("ratelimit:*");
        if (rateLimitKeys != null && !rateLimitKeys.isEmpty()) {
            stringRedisTemplate.delete(rateLimitKeys);
        }
    }

    @Test
    @DisplayName("[JWT HTTP] 로그인 → JWT 발급 → 해당 JWT로 API 호출 → 200 OK")
    void validToken_allowsAccess() {
        // 로그인하여 JWT 발급
        assertThat(mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"user1\"}"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.token")
                .asString()
                .isNotEqualTo("dummy"); // 실제 토큰이 반환되어야 함

        // 직접 토큰 생성하여 API 호출 테스트
        String token = jwtTokenService.generateToken("user1");
        assertThat(mvc.post().uri("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000}")
                .header("Authorization", "Bearer " + token))
                .hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[JWT HTTP] 로그아웃 후 동일 JWT로 API 호출 → 401 Unauthorized")
    void blacklistedToken_returns401() {
        String token = jwtTokenService.generateToken("user1");

        // 로그아웃
        assertThat(mvc.post().uri("/api/auth/logout")
                .header("Authorization", "Bearer " + token))
                .hasStatus(HttpStatus.OK);

        // 동일 토큰으로 API 호출 → 401
        assertThat(mvc.post().uri("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000}")
                .header("Authorization", "Bearer " + token))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("[JWT HTTP] Authorization 헤더 없이 API 호출 → 200 OK (선택적 인증)")
    void noAuthHeader_allowsAccess() {
        assertThat(mvc.post().uri("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000}"))
                .hasStatus(HttpStatus.OK);
    }
}
