package com.example.wepay.service;

import com.example.wepay.domain.Account;
import com.example.wepay.repository.AccountRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service("mutexLock")
class MutexLockCacheServiceImpl implements AccountCacheService {

    private static final String CACHE_PREFIX = "account:";
    private static final String LOCK_PREFIX = "lock:account:";

    private final AccountRepository accountRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final Duration cacheTtl;

    MutexLockCacheServiceImpl(AccountRepository accountRepository,
                              RedisTemplate<String, Object> redisTemplate,
                              RedissonClient redissonClient,
                              @Value("${cache.account.ttl-seconds:60}") long ttlSeconds) {
        this.accountRepository = accountRepository;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.cacheTtl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Account getAccount(Long id) {
        String cacheKey = CACHE_PREFIX + id;

        // 1. 캐시 조회 → 히트면 반환
        Account cached = fromCache(cacheKey);
        if (cached != null) return cached;

        // 2. Mutex Lock 획득 시도
        RLock lock = redissonClient.getLock(LOCK_PREFIX + id);
        try {
            boolean acquired = lock.tryLock(5, 1, TimeUnit.SECONDS);
            if (acquired) {
                try {
                    // 3. Double-check: 락 획득 후 캐시 재확인
                    Account doubleChecked = fromCache(cacheKey);
                    if (doubleChecked != null) return doubleChecked;

                    // 4. DB 조회 → 캐시 저장
                    Account account = accountRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
                    redisTemplate.opsForValue().set(cacheKey, account, cacheTtl);
                    return account;
                } finally {
                    lock.unlock();
                }
            } else {
                // 5. 락 미획득 → 대기 후 캐시 재조회 (다른 스레드가 갱신했을 것)
                TimeUnit.MILLISECONDS.sleep(50);
                Account retried = fromCache(cacheKey);
                if (retried != null) return retried;
                throw new RuntimeException("Cache miss after mutex wait for account: " + id);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for lock", e);
        }
    }

    @Override
    public void evictAccount(Long id) {
        redisTemplate.delete(CACHE_PREFIX + id);
    }

    private Account fromCache(String cacheKey) {
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        return cached instanceof Account account ? account : null;
    }
}
