package com.example.wepay.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheWrapperTest {

    @Test
    @DisplayName("[isExpired] 과거 expireAt이면 true 반환")
    void isExpired_pastTime_returnsTrue() {
        long pastTime = System.currentTimeMillis() - 1000;
        CacheWrapper<String> wrapper = new CacheWrapper<>("data", pastTime);

        assertThat(wrapper.isExpired()).isTrue();
    }

    @Test
    @DisplayName("[isExpired] 미래 expireAt이면 false 반환")
    void isExpired_futureTime_returnsFalse() {
        long futureTime = System.currentTimeMillis() + 60_000;
        CacheWrapper<String> wrapper = new CacheWrapper<>("data", futureTime);

        assertThat(wrapper.isExpired()).isFalse();
    }
}
