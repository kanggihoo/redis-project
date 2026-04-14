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

/**
 * 논리적 만료(Logical Expiration) 기법을 사용하여 계정 캐시를 관리하는 서비스 구현체입니다.
 *
 * <p>
 * 이 구현체는 Cache Stampede 문제를 해결하기 위해 다음 전략을 사용합니다:
 * 1. Redis 자체의 TTL 기능을 사용하는 대신, 캐시 데이터 내부에 '만료 시간'을 포함하는
 * Wrapper(AccountCacheWrapper)를 저장합니다.
 * 2. 캐시 조회 시 논리적 만료 시간이 지난 경우에도 즉시 기존(Stale) 데이터를 반환하여 응답 지연을 방지합니다.
 * 3. 만료된 데이터에 대해서는 백그라운드 스레드에서 비동기적으로 DB 데이터를 조회하여 캐시를 갱신합니다.
 * 4. ConcurrentHashMap을 이용한 Rebuild Flag를 통해 동일한 키에 대한 중복 갱신 작업을 방지합니다.
 */
@Service("logicalExpiration")
class LogicalExpirationCacheServiceImpl implements AccountCacheService {

    private final AccountRepository accountRepository;

    private final RedisTemplate<String, Object> redisTemplate;
    private final long logicalTtlMillis;

    /**
     * 캐시 재구축 작업이 중복으로 발생하지 않도록 제어하는 플래그 맵입니다.
     * key: 캐시 키, value: 진행 여부 (Boolean.TRUE)
     */
    private final ConcurrentHashMap<String, Boolean> rebuildFlags = new ConcurrentHashMap<>();

    /**
     * 비동기 캐시 갱신을 수행하기 위한 백그라운드 스레드 풀입니다.
     */
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool();

    /**
     * @param accountRepository DB 조회를 위한 리포지토리
     * @param redisTemplate     Redis 접근을 위한 템플릿
     * @param logicalTtlSeconds 설정 파일(`cache.account.logical-ttl-seconds`)에서 주입받는
     *                          논리적 TTL 시간(초 단위)
     */
    LogicalExpirationCacheServiceImpl(AccountRepository accountRepository,
            RedisTemplate<String, Object> redisTemplate,
            @Value("${cache.account.logical-ttl-seconds:60}") long logicalTtlSeconds) {
        this.accountRepository = accountRepository;
        this.redisTemplate = redisTemplate;
        this.logicalTtlMillis = logicalTtlSeconds * 1000L;
    }

    /**
     * 계정 정보를 조회합니다.
     * 캐시가 히트되고 만료된 경우, 백그라운드에서 비동기 갱신을 수행하며 현재 데이터를 즉시 반환합니다.
     *
     * @param id 조회할 계정 ID
     * @return 조회된 계정 정보
     * @throws IllegalArgumentException 계정이 DB에서 발견되지 않을 경우 발생
     */
    @Override
    public Account getAccount(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        Object raw = redisTemplate.opsForValue().get(cacheKey);

        // 1. 캐시 히트 확인 (AccountCacheWrapper 형태로 캐싱됨)
        if (raw instanceof AccountCacheWrapper wrapper) {
            Account account = wrapper.getData();

            // 2. 내부 타임스탬프를 이용한 논리적 만료 확인
            if (wrapper.isExpired()) {
                // [Cache Stampede 대응 핵심 로직]
                // 3. 만료되었더라도 즉시 기존 데이터(Stale Data)를 반환하여 클라이언트 대기 방지

                // 4. 비동기 백그라운드 갱신 시도 (중복 실행 방지)
                // 이미 동일 키에 대해 리빌드 중이라면 추가 작업을 수행하지 않음 (putIfAbsent)
                if (rebuildFlags.putIfAbsent(cacheKey, Boolean.TRUE) == null) {
                    backgroundExecutor.submit(() -> {
                        try {
                            // 리포지토리에서 최신 정보를 읽어와 Redis 갱신
                            rebuild(id, cacheKey);
                        } finally {
                            // 작업 완료 후 플래그 제거하여 다음 갱신 기회를 열어둠
                            rebuildFlags.remove(cacheKey);
                        }
                    });
                }
                return account;
            }
            // 5. 만료되지 않은 경우 캐시 데이터 즉시 반환
            return account;
        }

        // 6. 캐시 미스 (Cold Start) 시나리오
        // 캐시가 아예 없는 경우 동기 방식으로 DB 조회 후 캐시 생성
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        saveWrapper(cacheKey, account);
        return account;
    }

    /**
     * 특정 계정의 캐시 데이터를 삭제합니다.
     */
    @Override
    public void evictAccount(Long id) {
        redisTemplate.delete(CACHE_PREFIX + id);
    }

    /**
     * 캐시 워밍업을 통해 데이터를 미리 로드합니다.
     * 초기 가동 시 Cold Start로 인한 DB 부하를 예방합니다.
     */
    public void warmUp(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        saveWrapper(cacheKey, account);
    }

    /**
     * 서비스 종료 시 백그라운드 스레드 풀을 안전하게 종료합니다.
     */
    @PreDestroy
    void shutdown() {
        backgroundExecutor.shutdown();
    }

    /**
     * DB에서 최신 데이터를 조회하여 Redis 캐시를 업데이트합니다.
     */
    private void rebuild(Long id, String cacheKey) {
        accountRepository.findById(id).ifPresent(account -> saveWrapper(cacheKey, account));
    }

    /**
     * 계정 정보를 AccountCacheWrapper로 감싸서 Redis에 저장합니다.
     * 만료 시간은 (현재 시간 + 설정된 논리 TTL)로 설정됩니다.
     */
    private void saveWrapper(String cacheKey, Account account) {
        long expireAt = System.currentTimeMillis() + logicalTtlMillis;
        // Redis 자체 TTL 대신 Wrapper의 expireAt 필드를 활용
        redisTemplate.opsForValue().set(cacheKey, new AccountCacheWrapper(account, expireAt));
    }
}
