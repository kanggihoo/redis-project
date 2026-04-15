package com.example.wepay.service;

import com.example.wepay.util.RedisKeyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service("slidingWindow")
public class SlidingWindowRateLimitService implements RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final int maxRequests;
    private final long windowSeconds;

    public SlidingWindowRateLimitService(StringRedisTemplate stringRedisTemplate,
            @Value("${rate-limit.transfer.max-requests}") int maxRequests,
            @Value("${rate-limit.transfer.window-seconds}") long windowSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public boolean isAllowed(String apiKey, String identifier) {
        String key = RedisKeyManager.getRateLimitKey("sliding", apiKey, identifier);
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000);

        // 윈도우 밖의 오래된 요소 제거
        stringRedisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // 현재 윈도우 내 요소 수 확인
        Long count = stringRedisTemplate.opsForZSet().zCard(key);
        if (count != null && count >= maxRequests) {
            return false;
        }

        // 새 요청 추가 (고유 member: timestamp:UUID)
        String member = now + ":" + java.util.UUID.randomUUID();
        stringRedisTemplate.opsForZSet().add(key, member, now);

        // 키 자체 TTL 설정 (메모리 안전망)
        stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);

        return true;
    }
}
