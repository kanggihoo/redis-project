package com.example.wepay.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StoreRankingServiceImpl implements StoreRankingService {

    private static final String RANKING_KEY = "store:ranking";

    private final StringRedisTemplate stringRedisTemplate;

    public StoreRankingServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void recordTransaction(String storeName) {
        stringRedisTemplate.opsForZSet().incrementScore(RANKING_KEY, storeName, 1);
    }

    @Override
    public List<String> getTopStores(int limit) {
        var result = stringRedisTemplate.opsForZSet().reverseRange(RANKING_KEY, 0, limit - 1);
        if (result == null) return List.of();
        return List.copyOf(result);
    }
}
