package com.example.wepay.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@link StoreRankingService}의 구현체로, Redis의 Sorted Set(ZSET)을 활용하여
 * 가맹점 거래 랭킹을 실시간으로 관리하는 서비스입니다.
 */
@Service
public class StoreRankingServiceImpl implements StoreRankingService {

    /**
     * Redis에서 랭킹 데이터를 저장할 키 값
     */
    private static final String RANKING_KEY = "store:ranking";

    private final StringRedisTemplate stringRedisTemplate;

    public StoreRankingServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 특정 가맹점의 거래 기록을 저장합니다.
     * <p>
     * Redis의 {@code ZINCRBY} 명령어를 사용하여 해당 가맹점(Member)의 
     * 거래 횟수(Score)를 1씩 증가시킵니다.
     * </p>
     *
     * @param storeName 거래를 기록할 가맹점 이름
     */
    @Override
    public void recordTransaction(String storeName) {
        // ZINCRBY store:ranking 1 {storeName}
        stringRedisTemplate.opsForZSet().incrementScore(RANKING_KEY, storeName, 1);
    }

    /**
     * 상위 N개의 인기 가맹점 목록을 조회합니다.
     * <p>
     * Redis의 {@code ZREVRANGE} 명령어를 사용하여 Score(거래 횟수)가 
     * 높은 순서대로 데이터를 가져옵니다.
     * </p>
     *
     * @param limit 조회할 상위 가맹점 수
     * @return 인기 가맹점 이름 목록 (내림차순 정렬)
     */
    @Override
    public List<String> getTopStores(int limit) {
        // ZREVRANGE store:ranking 0 {limit-1}
        var result = stringRedisTemplate.opsForZSet().reverseRange(RANKING_KEY, 0, limit - 1);
        if (result == null) return List.of();
        
        // 검색 결과를 불변 리스트로 복사하여 반환
        return List.copyOf(result);
    }
}
