package com.example.wepay.service;

import com.example.wepay.domain.Account;
import com.example.wepay.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
class AccountCacheServiceImpl implements AccountCacheService {

    // Cache Aside: account:{id}  / TTL 설정값(기본 60초) / JSON 직렬화
    private static final String CACHE_PREFIX = "account:";
    private final Duration CACHE_TTL;

    // Null Caching: null:account:{id} / TTL 30초 / String "NULL" 마커
    private static final String NULL_PREFIX = "null:account:";
    private static final String NULL_MARKER = "NULL";
    private static final Duration NULL_TTL = Duration.ofSeconds(30);

    private final AccountRepository accountRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    AccountCacheServiceImpl(AccountRepository accountRepository,
                            RedisTemplate<String, Object> redisTemplate,
                            StringRedisTemplate stringRedisTemplate,
                            @Value("${cache.account.ttl-seconds:60}") long ttlSeconds) {
        this.accountRepository = accountRepository;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.CACHE_TTL = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Account getAccount(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        String nullKey = NULL_PREFIX + id;

        // 1) Null 캐시 히트 → 이미 존재하지 않음을 알고 있으므로 DB 미호출
        if (NULL_MARKER.equals(stringRedisTemplate.opsForValue().get(nullKey))) {
            throw new IllegalArgumentException("Account not found: " + id);
        }

        // 2) Cache Hit
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Account account) {
            return account;
        }

        // 3) Cache Miss → DB 조회
        return accountRepository.findById(id)
                .map(account -> {
                    redisTemplate.opsForValue().set(cacheKey, account, CACHE_TTL);
                    return account;
                })
                .orElseGet(() -> {
                    // DB에도 없음 → Null 마커 캐싱 (Cache Penetration 방지)
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
