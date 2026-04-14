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

/**
 * Mutex Lock(분산 락)을 이용한 캐시 스탬피드 대응 전략 구현체입니다.
 * 
 * 캐시가 만료되었을 때 여러 스레드가 동시에 DB를 조회하는 것을 방지하기 위해,
 * 첫 번째로 진입한 스레드만 락을 획득하여 DB를 조회하고 결과를 캐싱합니다.
 * 나머지 스레드들은 락 획득을 기다리거나 잠시 대기 후 캐시된 데이터를 조회합니다.
 */
@Service("mutexLock")
class MutexLockCacheServiceImpl implements AccountCacheService {

    private static final String LOCK_PREFIX = "lock:account:";


    private final AccountRepository accountRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final Duration cacheTtl;

    /**
     * @param accountRepository DB 조회를 위한 리포지토리
     * @param redisTemplate     캐시 저장을 위한 Redis 템플릿
     * @param redissonClient    분산 락(Redisson) 클라이언트
     * @param ttlSeconds        캐시 만료 시간 (설정값이 없으면 기본 60초)
     */
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

        // 1. 1차 캐시 조회: 데이터가 있으면 즉시 반환 (대부분의 케이스)
        Account cached = fromCache(cacheKey);
        if (cached != null)
            return cached;

        // => 캐시 미스(Cache Miss) 발생 시
        // 2. Mutex Lock 획득 시도: 동일한 ID에 대해 하나의 스레드만 DB 접근 권한을 가짐
        // LOCK_PREFIX + id를 키로 사용하여 계좌별로 독립적인 락을 생성
        RLock lock = redissonClient.getLock(LOCK_PREFIX + id);
        try {
            // 최대 5초 락 대기, 락 획득 시 1초간 유지 (WaitTime: 5s, LeaseTime: 1s)
            boolean acquired = lock.tryLock(5, 1, TimeUnit.SECONDS);

            if (acquired) {
                try {
                    // 3. Double-check(이중 확인): 락을 기다리는 동안 다른 스레드가 이미 DB에서 가져와 캐싱했을 수 있음
                    Account doubleChecked = fromCache(cacheKey);
                    if (doubleChecked != null)
                        return doubleChecked;

                    // 4. DB 조회 및 캐시 갱신: 락을 선점한 유일한 스레드가 실행
                    Account account = accountRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));

                    // 조회 결과를 Redis에 저장하고 반환
                    redisTemplate.opsForValue().set(cacheKey, account, cacheTtl);
                    return account;
                } finally {
                    // 락 해제
                    lock.unlock();
                }
            } else {
                // 5. 락 획득 실패 시: 락을 얻은 스레드가 DB 데이터를 캐시에 채울 때까지 잠시 대기
                TimeUnit.MILLISECONDS.sleep(50);

                // 대기 후 다시 캐시 확인
                Account retried = fromCache(cacheKey);
                if (retried != null)
                    return retried;

                // 재조회도 실패하면 예외 처리 (설정에 따라 DB 직접 조회를 허용할 수도 있음)
                throw new RuntimeException("Cache miss after mutex wait for account: " + id);
            }
        } catch (InterruptedException e) {
            // 현재 스레드의 인터럽트 상태 유지
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for lock", e);
        }
    }

    /**
     * 수동으로 캐시를 삭제할 때 사용합니다.
     */
    @Override
    public void evictAccount(Long id) {
        redisTemplate.delete(CACHE_PREFIX + id);
    }

    /**
     * Redis에서 데이터를 읽어와 Account 객체로 변환합니다.
     */
    private Account fromCache(String cacheKey) {
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        return cached instanceof Account account ? account : null;
    }
}
