package com.example.wepay.service;

import com.example.wepay.repository.StoreTransactionRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TransactionCounterServiceImpl implements TransactionCounterService {

    // 키 전략: store:txcount:{storeId} / String 타입 / TTL 없음 (배치 flush)
    private static final String KEY_PREFIX = "store:txcount:";

    private final StringRedisTemplate stringRedisTemplate;
    private final StoreTransactionRepository storeTransactionRepository;

    TransactionCounterServiceImpl(StringRedisTemplate stringRedisTemplate,
                                  StoreTransactionRepository storeTransactionRepository) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.storeTransactionRepository = storeTransactionRepository;
    }

    @Override
    public void increment(Long storeId) {
        stringRedisTemplate.opsForValue().increment(KEY_PREFIX + storeId);
    }

    @Override
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void flushToDB() {
        var keys = stringRedisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            // GETDEL: 값을 읽고 즉시 삭제 (atomic)
            String raw = stringRedisTemplate.opsForValue().getAndDelete(key);
            if (raw == null) continue;

            long delta = Long.parseLong(raw);
            // storeId 추출: "store:txcount:{storeId}" → split 후 마지막 토큰
            Long storeId = Long.parseLong(key.substring(KEY_PREFIX.length()));
            storeTransactionRepository.upsertCount(storeId, delta);
        }
    }
}
