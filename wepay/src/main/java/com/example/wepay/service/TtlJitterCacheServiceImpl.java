package com.example.wepay.service;

import com.example.wepay.domain.Account;
import com.example.wepay.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * TTL Jitter(랜덤 만료) 전략을 사용하는 {@link AccountCacheService} 구현체입니다.
 * <p>
 * 이 전략은 캐시 아이템의 만료 시간에 무작위 지연(Jitter)을 추가하여, 
 * 여러 캐시 키가 동시에 만료되어 발생하는 "캐시 스탬피드(Cache Stampede)" 현상을 방지합니다.
 * 단일 핫 키(Hot Key)에 대한 스탬피드를 완전히 해결하지는 못하지만, 
 * 다수의 서로 다른 키들이 동일한 시점에 만료되어 DB 부하가 급증하는 것을 효과적으로 분산시킵니다.
 * </p>
 * <p>
 * 또한, 존재하지 않는 ID에 대한 불필요한 DB 조회를 방지하기 위해 
 * "Null Caching"을 구현하여 "캐시 관통(Cache Penetration)" 공격을 방어합니다.
 * </p>
 */
@Service("ttlJitter")
class TtlJitterCacheServiceImpl implements AccountCacheService {

    /**
     * 계정 정보가 없음을 캐싱하기 위한 접두사 (Null Caching용)
     */
    private static final String NULL_PREFIX = "null:account:";

    /**
     * Redis에 저장할 실제 데이터가 없음을 나타내는 마커 값
     */
    private static final String NULL_MARKER = "NULL";

    /**
     * Null 마커의 TTL. 계정이 생성될 경우의 일관성을 위해 짧게(30초) 설정합니다.
     */
    private static final Duration NULL_TTL = Duration.ofSeconds(30);

    private final AccountRepository accountRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final Duration baseTtl;
    private final long jitterMaxSeconds;

    /**
     * TtlJitterCacheServiceImpl 생성자
     *
     * @param accountRepository 데이터베이스 접근을 위한 리포지토리
     * @param redisTemplate     객체(Account) 저장을 위한 Redis 템플릿
     * @param stringRedisTemplate 문자열 마커(Null Caching) 저장을 위한 Redis 템플릿
     * @param ttlSeconds          기본 만료 시간 (설정: {@code cache.account.ttl-seconds})
     * @param jitterMaxSeconds    최대 랜덤 추가 시간 (설정: {@code cache.account.jitter-max-seconds})
     */
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

    /**
     * TTL Jitter를 적용하여 캐시 또는 데이터베이스에서 계정 정보를 조회합니다.
     *
     * @param id 계정 ID
     * @return 조회된 계정 정보
     * @throws IllegalArgumentException 계정이 존재하지 않는 경우 (Null 캐시 히트 또는 DB 부재)
     */
    @Override
    public Account getAccount(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        String nullKey = NULL_PREFIX + id;

        // 1. 캐시 관통(Cache Penetration) 방지를 위해 Null 캐시 확인
        if (NULL_MARKER.equals(stringRedisTemplate.opsForValue().get(nullKey))) {
            throw new IllegalArgumentException("Account not found: " + id);
        }

        // 2. 메인 캐시에서 데이터 조회 시도
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Account account) {
            return account;
        }

        // 3. 캐시 미스: DB에서 조회 후 TTL Jitter를 적용하여 캐싱
        return accountRepository.findById(id)
                .map(account -> {
                    // 만료 시간을 분산시키기 위해 Jitter 계산
                    // jitteredTtl = 기본TTL + random(0, 최대Jitter)
                    Duration jitteredTtl = baseTtl.plusSeconds(
                            ThreadLocalRandom.current().nextLong(0, jitterMaxSeconds + 1));
                    
                    redisTemplate.opsForValue().set(cacheKey, account, jitteredTtl);
                    return account;
                })
                .orElseGet(() -> {
                    // 4. DB에도 없는 경우 Null 마커 저장 (Null Caching)
                    stringRedisTemplate.opsForValue().set(nullKey, NULL_MARKER, NULL_TTL);
                    throw new IllegalArgumentException("Account not found: " + id);
                });
    }

    /**
     * 메인 데이터 캐시와 Null 캐시 모두에서 해당 계정 정보를 삭제합니다.
     *
     * @param id 삭제할 계정 ID
     */
    @Override
    public void evictAccount(Long id) {
        redisTemplate.delete(CACHE_PREFIX + id);
        stringRedisTemplate.delete(NULL_PREFIX + id);
    }
}
