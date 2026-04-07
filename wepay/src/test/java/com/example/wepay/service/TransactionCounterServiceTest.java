package com.example.wepay.service;

import com.example.wepay.repository.StoreTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(com.example.wepay.TestcontainersConfiguration.class)
@Sql(statements = "DELETE FROM store_transactions", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = "DELETE FROM store_transactions", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TransactionCounterServiceTest {

    @Autowired
    TransactionCounterService transactionCounterService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    StoreTransactionRepository storeTransactionRepository;

    @BeforeEach
    void clearRedis() {
        var keys = stringRedisTemplate.keys("store:txcount:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("[INCR] increment() 호출 시 Redis 카운터가 1씩 증가한다")
    void increment_increasesRedisCounter() {
        Long storeId = 1L;
        String key = "store:txcount:" + storeId;

        transactionCounterService.increment(storeId);
        transactionCounterService.increment(storeId);
        transactionCounterService.increment(storeId);

        String value = stringRedisTemplate.opsForValue().get(key);
        assertThat(value).isEqualTo("3");
    }

    @Test
    @DisplayName("[Flush] flushToDB() 호출 시 Redis 카운터가 DB에 반영되고 Redis 키가 삭제된다")
    void flushToDB_persistsCounterAndClearsRedis() {
        // given: 스토어 1에 5회, 스토어 2에 3회 카운터 쌓기
        for (int i = 0; i < 5; i++) transactionCounterService.increment(1L);
        for (int i = 0; i < 3; i++) transactionCounterService.increment(2L);

        // when
        transactionCounterService.flushToDB();

        // then: DB에 반영 확인
        var store1 = storeTransactionRepository.findById(1L);
        var store2 = storeTransactionRepository.findById(2L);

        assertThat(store1).isPresent();
        assertThat(store1.get().getTxCount()).isEqualTo(5L);

        assertThat(store2).isPresent();
        assertThat(store2.get().getTxCount()).isEqualTo(3L);

        // and: Redis 키 삭제 확인 (flush 후 카운터 초기화)
        assertThat(stringRedisTemplate.keys("store:txcount:*")).isEmpty();
    }
}
