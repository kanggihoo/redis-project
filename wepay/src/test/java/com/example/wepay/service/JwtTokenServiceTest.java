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
class JwtTokenServiceTest {

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
    }

    @Test
    @DisplayName("[JWT] 토큰 발급 후 parseUserId로 userId를 추출할 수 있다")
    void generateAndParse_returnsCorrectUserId() {
        String token = jwtTokenService.generateToken("user1");

        assertThat(token).isNotBlank();
        assertThat(jwtTokenService.parseUserId(token)).isEqualTo("user1");
    }

    @Test
    @DisplayName("[JWT Blacklist] blacklist 등록 후 isBlacklisted가 true를 반환한다")
    void blacklist_makesTokenBlacklisted() {
        String token = jwtTokenService.generateToken("user1");

        jwtTokenService.blacklist(token);

        assertThat(jwtTokenService.isBlacklisted(token)).isTrue();
    }

    @Test
    @DisplayName("[JWT Blacklist] blacklist 미등록 토큰은 isBlacklisted가 false를 반환한다")
    void nonBlacklisted_returnsFalse() {
        String token = jwtTokenService.generateToken("user1");

        assertThat(jwtTokenService.isBlacklisted(token)).isFalse();
    }

    @Test
    @DisplayName("[JWT Blacklist TTL] blacklist 등록 시 Redis 키의 TTL이 토큰 잔여 유효시간 이하이다")
    void blacklist_setsCorrectTtl() {
        String token = jwtTokenService.generateToken("user1");
        String jti = jwtTokenService.parseJti(token);

        jwtTokenService.blacklist(token);

        Long ttl = stringRedisTemplate.getExpire("blacklist:jwt:" + jti);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(3600);
    }
}
