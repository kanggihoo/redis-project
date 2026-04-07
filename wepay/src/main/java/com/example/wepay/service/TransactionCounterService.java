package com.example.wepay.service;

public interface TransactionCounterService {

    /** Redis INCR 으로 storeId 의 거래 카운터를 1 증가시킨다. */
    void increment(Long storeId);

    /** Redis 에 쌓인 카운터를 DB 에 벌크 반영하고 키를 삭제한다. */
    void flushToDB();
}
