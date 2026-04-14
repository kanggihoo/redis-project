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

/**
 * Redis Sorted Set(ZSET)을 이용한 가맹점 거래 랭킹 서비스 테스트입니다.
 * <p>
 * 실시간으로 변하는 가맹점별 거래 횟수를 Redis에 저장하고, 
 * 가장 거래가 많은 순으로 조회가 잘 되는지 검증합니다.
 * </p>
 */
@SpringBootTest
@Import(com.example.wepay.TestcontainersConfiguration.class)
class StoreRankingServiceTest {

    @Autowired
    StoreRankingService storeRankingService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    /**
     * 각 테스트 시작 전 Redis에 저장된 기존 랭킹 데이터를 삭제하여 
     * 테스트 간 독립성을 보장합니다.
     */
    @BeforeEach
    void clearRanking() {
        stringRedisTemplate.delete("store:ranking");
    }

    /**
     * 서로 다른 거래 횟수를 가진 가맹점들을 등록했을 때, 
     * 거래 횟수(Score)가 높은 순서대로 정확히 정렬되어 반환되는지 테스트합니다.
     */
    @Test
    @DisplayName("StarBucks 5회, McDonald 3회, Subway 7회 거래 후 상위 2개 조회 → [Subway, StarBucks]")
    void getTopStores_returnsTopTwoInCorrectOrder() {
        // [Arrange] 가맹점별 거래 기록 시뮬레이션
        // Subway(7) > StarBucks(5) > McDonald(3) 순위가 형성되어야 함
        for (int i = 0; i < 5; i++) storeRankingService.recordTransaction("StarBucks");
        for (int i = 0; i < 3; i++) storeRankingService.recordTransaction("McDonald");
        for (int i = 0; i < 7; i++) storeRankingService.recordTransaction("Subway");

        // [Act] 상위 2개 가맹점 조회 호출
        List<String> top2 = storeRankingService.getTopStores(2);

        // [Then] 랭킹 결과와 순서 검증
        // 7회인 Subway가 1위, 5회인 StarBucks가 2위여야 하며 McDonald는 제외되어야 함
        assertThat(top2).containsExactly("Subway", "StarBucks");
    }

    /**
     * DB에 저장된 전체 가맹점 수보다 요청한 limit 값이 큰 경우, 
     * 오류 없이 현재 존재하는 모든 가맹점을 반환하는지 테스트합니다.
     */
    @Test
    @DisplayName("limit보다 가맹점 수가 적으면 전체 반환")
    void getTopStores_whenLessThanLimit_returnsAll() {
        // [Arrange] 가맹점 1곳만 등록
        storeRankingService.recordTransaction("OnlyStore");

        // [Act] 10개 조회를 요청 (실제 데이터는 1개뿐)
        List<String> result = storeRankingService.getTopStores(10);

        // [Then] 존재하는 1개의 데이터만 정상적으로 반환되었는지 확인
        assertThat(result).hasSize(1)
                .containsExactly("OnlyStore");
    }
}
