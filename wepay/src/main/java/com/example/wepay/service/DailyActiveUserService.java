package com.example.wepay.service;

public interface DailyActiveUserService {
    void recordActiveUser(Long userId);
    long getDailyActiveUserCount();
}
