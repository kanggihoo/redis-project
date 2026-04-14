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
class StoreLocationServiceTest {

    @Autowired
    StoreLocationService storeLocationService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearGeo() {
        stringRedisTemplate.delete("store:locations");
    }

    @Test
    @DisplayName("강남 기준 3km 반경 검색 → 강남만 포함 (홍대·잠실은 10km+ 거리)")
    void findNearbyStores_within3km_returnsOnlyGangnam() {
        // given — 홍대(약 11km), 잠실(약 5.3km), 강남(0km)
        storeLocationService.addStore("강남점", 127.0495, 37.5030);
        storeLocationService.addStore("홍대점", 126.9246, 37.5563);
        storeLocationService.addStore("잠실점", 127.1000, 37.5133);

        // when: 강남 기준 3km
        List<String> nearby = storeLocationService.findNearbyStores(127.0495, 37.5030, 3.0);

        // then
        assertThat(nearby).containsExactlyInAnyOrder("강남점");
        assertThat(nearby).doesNotContain("홍대점", "잠실점");
    }

    @Test
    @DisplayName("강남 기준 15km 반경 검색 → 3곳 모두 포함")
    void findNearbyStores_within15km_returnsAllThree() {
        // given
        storeLocationService.addStore("강남점", 127.0495, 37.5030);
        storeLocationService.addStore("홍대점", 126.9246, 37.5563);
        storeLocationService.addStore("잠실점", 127.1000, 37.5133);

        // when: 강남 기준 15km
        List<String> nearby = storeLocationService.findNearbyStores(127.0495, 37.5030, 15.0);

        // then
        assertThat(nearby).containsExactlyInAnyOrder("강남점", "홍대점", "잠실점");
    }
}
