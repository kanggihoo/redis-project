package com.example.wepay.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service("fixedWindow")
public class FixedWindowRateLimitService implements RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final int maxRequests;
    private final long windowSeconds;

    public FixedWindowRateLimitService(StringRedisTemplate stringRedisTemplate,
                                       @Value("${rate-limit.transfer.max-requests}") int maxRequests,
                                       @Value("${rate-limit.transfer.window-seconds}") long windowSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public boolean isAllowed(String apiKey, String identifier) {
        String key = buildKey(apiKey, identifier);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, windowSeconds, java.util.concurrent.TimeUnit.SECONDS);
        }
        return count != null && count <= maxRequests;
    }

    private String buildKey(String apiKey, String identifier) {
        return "ratelimit:fixed:" + apiKey + ":" + identifier;
    }
}
