package com.example.wepay.service;

import com.example.wepay.cache.AccountCacheWrapper;
import com.example.wepay.domain.Account;
import com.example.wepay.repository.AccountRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service("logicalExpiration")
class LogicalExpirationCacheServiceImpl implements AccountCacheService {

    private static final String CACHE_PREFIX = "account:";

    private final AccountRepository accountRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final long logicalTtlMillis;
    private final ConcurrentHashMap<String, Boolean> rebuildFlags = new ConcurrentHashMap<>();
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool();

    LogicalExpirationCacheServiceImpl(AccountRepository accountRepository,
                                      RedisTemplate<String, Object> redisTemplate,
                                      @Value("${cache.account.logical-ttl-seconds:60}") long logicalTtlSeconds) {
        this.accountRepository = accountRepository;
        this.redisTemplate = redisTemplate;
        this.logicalTtlMillis = logicalTtlSeconds * 1000L;
    }

    @Override
    public Account getAccount(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        Object raw = redisTemplate.opsForValue().get(cacheKey);

        if (raw instanceof AccountCacheWrapper wrapper) {
            Account account = wrapper.getData();

            if (wrapper.isExpired()) {
                // 만료됐지만 stale 데이터 즉시 반환 + 백그라운드 갱신 (중복 방지)
                if (rebuildFlags.putIfAbsent(cacheKey, Boolean.TRUE) == null) {
                    backgroundExecutor.submit(() -> {
                        try {
                            rebuild(id, cacheKey);
                        } finally {
                            rebuildFlags.remove(cacheKey);
                        }
                    });
                }
                return account;
            }
            return account;
        }

        // 캐시 없음(cold start) → 동기 DB 조회
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        saveWrapper(cacheKey, account);
        return account;
    }

    @Override
    public void evictAccount(Long id) {
        redisTemplate.delete(CACHE_PREFIX + id);
    }

    public void warmUp(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        saveWrapper(cacheKey, account);
    }

    @PreDestroy
    void shutdown() {
        backgroundExecutor.shutdown();
    }

    private void rebuild(Long id, String cacheKey) {
        accountRepository.findById(id).ifPresent(account -> saveWrapper(cacheKey, account));
    }

    private void saveWrapper(String cacheKey, Account account) {
        long expireAt = System.currentTimeMillis() + logicalTtlMillis;
        redisTemplate.opsForValue().set(cacheKey, new AccountCacheWrapper(account, expireAt));
    }
}
