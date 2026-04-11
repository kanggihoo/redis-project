package com.example.wepay.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(com.example.wepay.TestcontainersConfiguration.class)
class StoreRankingServiceTest {

    @Autowired
    StoreRankingService storeRankingService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearRanking() {
        stringRedisTemplate.delete("store:ranking");
    }

    @Test
    @DisplayName("StarBucks 5회, McDonald 3회, Subway 7회 거래 후 상위 2개 조회 → [Subway, StarBucks]")
    void getTopStores_returnsTopTwoInCorrectOrder() {
        // given
        for (int i = 0; i < 5; i++) storeRankingService.recordTransaction("StarBucks");
        for (int i = 0; i < 3; i++) storeRankingService.recordTransaction("McDonald");
        for (int i = 0; i < 7; i++) storeRankingService.recordTransaction("Subway");

        // when
        List<String> top2 = storeRankingService.getTopStores(2);

        // then
        assertThat(top2).containsExactly("Subway", "StarBucks");
    }

    @Test
    @DisplayName("limit보다 가맹점 수가 적으면 전체 반환")
    void getTopStores_whenLessThanLimit_returnsAll() {
        // given
        storeRankingService.recordTransaction("OnlyStore");

        // when
        List<String> result = storeRankingService.getTopStores(10);

        // then
        assertThat(result).containsExactly("OnlyStore");
    }
}
