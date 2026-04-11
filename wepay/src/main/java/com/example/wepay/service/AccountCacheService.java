package com.example.wepay.service;

import com.example.wepay.domain.Account;

public interface AccountCacheService {

    /**
     * 계좌 조회 (Cache Aside).
     * Redis 에 있으면 캐시에서, 없으면 DB 에서 조회 후 캐시에 저장한다.
     */
    Account getAccount(Long id);

    /**
     * 계좌 캐시 무효화.
     * 잔액 변경 후 낡은 캐시를 삭제하는 데 사용한다.
     */
    void evictAccount(Long id);
}
