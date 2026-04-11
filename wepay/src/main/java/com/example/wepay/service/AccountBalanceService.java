package com.example.wepay.service;

public interface AccountBalanceService {

    /**
     * 잔액 변경.
     * 내부적으로 DB UPDATE 후 캐시를 무효화한다.
     */
    void updateBalance(Long accountId, Long newBalance);
}
