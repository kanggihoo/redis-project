package com.example.wepay.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class DailyActiveUserServiceImpl implements DailyActiveUserService {

    private final StringRedisTemplate stringRedisTemplate;

    public DailyActiveUserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void recordActiveUser(Long userId) {
        stringRedisTemplate.opsForHyperLogLog().add(todayKey(), userId.toString());
    }

    @Override
    public long getDailyActiveUserCount() {
        Long count = stringRedisTemplate.opsForHyperLogLog().size(todayKey());
        return count != null ? count : 0L;
    }

    private String todayKey() {
        return "dau:" + LocalDate.now();
    }
}
