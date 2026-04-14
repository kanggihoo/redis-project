package com.example.wepay.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class JwtTokenService {

    private final StringRedisTemplate stringRedisTemplate;
    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtTokenService(StringRedisTemplate stringRedisTemplate,
                            @Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration-seconds}") long expirationSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(String userId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationSeconds * 1000);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public String parseUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public String parseJti(String token) {
        return parseClaims(token).getId();
    }

    public void blacklist(String token) {
        String jti = parseJti(token);
        Date expiration = parseClaims(token).getExpiration();
        long remainingSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;

        if (remainingSeconds > 0) {
            stringRedisTemplate.opsForValue()
                    .set("blacklist:jwt:" + jti, "1", remainingSeconds, TimeUnit.SECONDS);
        }
    }

    public boolean isBlacklisted(String token) {
        String jti = parseJti(token);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey("blacklist:jwt:" + jti));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
