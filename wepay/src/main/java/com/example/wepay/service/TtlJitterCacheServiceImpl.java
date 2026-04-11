package com.example.wepay.service;

import com.example.wepay.domain.Account;
import com.example.wepay.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Service("ttlJitter")
class TtlJitterCacheServiceImpl implements AccountCacheService {

    private static final String CACHE_PREFIX = "account:";
    private static final String NULL_PREFIX = "null:account:";
    private static final String NULL_MARKER = "NULL";
    private static final Duration NULL_TTL = Duration.ofSeconds(30);

    private final AccountRepository accountRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final Duration baseTtl;
    private final long jitterMaxSeconds;

    TtlJitterCacheServiceImpl(AccountRepository accountRepository,
                              RedisTemplate<String, Object> redisTemplate,
                              StringRedisTemplate stringRedisTemplate,
                              @Value("${cache.account.ttl-seconds:60}") long ttlSeconds,
                              @Value("${cache.account.jitter-max-seconds:30}") long jitterMaxSeconds) {
        this.accountRepository = accountRepository;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.baseTtl = Duration.ofSeconds(ttlSeconds);
        this.jitterMaxSeconds = jitterMaxSeconds;
    }

    @Override
    public Account getAccount(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        String nullKey = NULL_PREFIX + id;

        if (NULL_MARKER.equals(stringRedisTemplate.opsForValue().get(nullKey))) {
            throw new IllegalArgumentException("Account not found: " + id);
        }

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Account account) {
            return account;
        }

        return accountRepository.findById(id)
                .map(account -> {
                    Duration jitteredTtl = baseTtl.plusSeconds(
                            ThreadLocalRandom.current().nextLong(0, jitterMaxSeconds + 1));
                    redisTemplate.opsForValue().set(cacheKey, account, jitteredTtl);
                    return account;
                })
                .orElseGet(() -> {
                    stringRedisTemplate.opsForValue().set(nullKey, NULL_MARKER, NULL_TTL);
                    throw new IllegalArgumentException("Account not found: " + id);
                });
    }

    @Override
    public void evictAccount(Long id) {
        redisTemplate.delete(CACHE_PREFIX + id);
        stringRedisTemplate.delete(NULL_PREFIX + id);
    }
}
