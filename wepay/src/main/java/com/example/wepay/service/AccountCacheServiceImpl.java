package com.example.wepay.service;

import com.example.wepay.domain.Account;
import com.example.wepay.repository.AccountRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
class AccountCacheServiceImpl implements AccountCacheService {

    // 키 전략: account:{id}  / TTL 60초 / JSON 직렬화
    private static final String KEY_PREFIX = "account:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final AccountRepository accountRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    AccountCacheServiceImpl(AccountRepository accountRepository,
                            RedisTemplate<String, Object> redisTemplate) {
        this.accountRepository = accountRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Account getAccount(Long id) {
        String key = KEY_PREFIX + id;

        // 1) Cache Hit
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof Account account) {
            return account;
        }

        // 2) Cache Miss → DB 조회 후 캐시 저장
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));

        redisTemplate.opsForValue().set(key, account, TTL);
        return account;
    }

    @Override
    public void evictAccount(Long id) {
        redisTemplate.delete(KEY_PREFIX + id);
    }
}
