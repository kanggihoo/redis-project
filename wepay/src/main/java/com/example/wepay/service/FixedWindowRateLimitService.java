package com.example.wepay.service;

import com.example.wepay.util.RedisKeyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

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
        String key = RedisKeyManager.getRateLimitKey("fixed", apiKey, identifier);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return count != null && count <= maxRequests;
    }
}
