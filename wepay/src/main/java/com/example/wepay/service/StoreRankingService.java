package com.example.wepay.service;

import java.util.List;

public interface StoreRankingService {
    void recordTransaction(String storeName);
    List<String> getTopStores(int limit);
}
