package com.example.wepay.controller;

import com.example.wepay.service.DailyActiveUserService;
import com.example.wepay.service.StoreLocationService;
import com.example.wepay.service.StoreRankingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebMvcTest(StoreController.class)
class StoreControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    StoreRankingService storeRankingService;

    @MockitoBean
    DailyActiveUserService dailyActiveUserService;

    @MockitoBean
    StoreLocationService storeLocationService;

    @Test
    @DisplayName("POST /api/stores/transactions/{storeName} → recordTransaction 호출")
    void recordTransaction_callsService() {
        assertThat(mvc.post().uri("/api/stores/transactions/StarBucks"))
                .hasStatusOk();

        verify(storeRankingService).recordTransaction("StarBucks");
    }

    @Test
    @DisplayName("GET /api/stores/ranking?limit=2 → 상위 가맹점 목록 반환")
    void getTopStores_returnsRankingList() {
        given(storeRankingService.getTopStores(2)).willReturn(List.of("Subway", "StarBucks"));

        assertThat(mvc.get().uri("/api/stores/ranking").param("limit", "2"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0]").isEqualTo("Subway");
    }

    @Test
    @DisplayName("POST /api/stores/dau/{userId} → recordActiveUser 호출")
    void recordActiveUser_callsService() {
        assertThat(mvc.post().uri("/api/stores/dau/42"))
                .hasStatusOk();

        verify(dailyActiveUserService).recordActiveUser(42L);
    }

    @Test
    @DisplayName("GET /api/stores/dau → 일별 활성 사용자 수 반환")
    void getDailyActiveUserCount_returnsCount() {
        given(dailyActiveUserService.getDailyActiveUserCount()).willReturn(100L);

        assertThat(mvc.get().uri("/api/stores/dau"))
                .hasStatusOk()
                .bodyJson().convertTo(Long.class).isEqualTo(100L);
    }

    @Test
    @DisplayName("POST /api/stores/locations → addStore 호출")
    void addStore_callsService() {
        assertThat(mvc.post().uri("/api/stores/locations")
                .param("storeName", "강남점")
                .param("longitude", "127.0495")
                .param("latitude", "37.5030"))
                .hasStatusOk();

        verify(storeLocationService).addStore("강남점", 127.0495, 37.5030);
    }

    @Test
    @DisplayName("GET /api/stores/nearby → 근처 가맹점 목록 반환")
    void findNearbyStores_returnsStoreList() {
        given(storeLocationService.findNearbyStores(127.0495, 37.5030, 5.0))
                .willReturn(List.of("강남점"));

        assertThat(mvc.get().uri("/api/stores/nearby")
                .param("lng", "127.0495")
                .param("lat", "37.5030")
                .param("radius", "5.0"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0]").isEqualTo("강남점");
    }
}
