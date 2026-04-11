package com.example.wepay.service;

import java.util.List;

public interface StoreLocationService {
    void addStore(String storeName, double longitude, double latitude);
    List<String> findNearbyStores(double longitude, double latitude, double radiusKm);
}
