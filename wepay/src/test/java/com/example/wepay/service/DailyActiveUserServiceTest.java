package com.example.wepay.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(com.example.wepay.TestcontainersConfiguration.class)
class DailyActiveUserServiceTest {

    @Autowired
    DailyActiveUserService dailyActiveUserService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearHll() {
        stringRedisTemplate.delete("dau:" + LocalDate.now());
    }

    @Test
    @DisplayName("userId 1,2,3,1,2 (5회 호출, 3명 유니크) → getDailyActiveUserCount() == 3")
    void recordActiveUser_uniqueUsers_countIsThree() {
        // when
        dailyActiveUserService.recordActiveUser(1L);
        dailyActiveUserService.recordActiveUser(2L);
        dailyActiveUserService.recordActiveUser(3L);
        dailyActiveUserService.recordActiveUser(1L);
        dailyActiveUserService.recordActiveUser(2L);

        // then
        assertThat(dailyActiveUserService.getDailyActiveUserCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("아무도 기록하지 않으면 count == 0")
    void getDailyActiveUserCount_whenEmpty_returnsZero() {
        assertThat(dailyActiveUserService.getDailyActiveUserCount()).isZero();
    }
}
