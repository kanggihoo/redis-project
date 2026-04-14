package com.example.wepay.service;

import java.util.List;

/**
 * 가맹점 거래 랭킹을 관리하는 서비스 인터페이스입니다.
 * Redis Sorted Set(ZSET)을 사용하여 실시간 랭킹을 구현합니다.
 */
public interface StoreRankingService {
    /**
     * 특정 가맹점의 거래 횟수를 1 증가시킵니다.
     * Redis Sorted Set의 Score를 1씩 증가시키는 방식으로 동작합니다.
     * 
     * @param storeName 거래가 발생한 가맹점 이름
     */
    void recordTransaction(String storeName);

    List<String> getTopStores(int limit);
}
