package com.example.wepay.service;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StoreLocationServiceImpl implements StoreLocationService {

    private static final String GEO_KEY = "store:locations";

    private final StringRedisTemplate stringRedisTemplate;

    public StoreLocationServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void addStore(String storeName, double longitude, double latitude) {
        stringRedisTemplate.opsForGeo().add(GEO_KEY, new Point(longitude, latitude), storeName);
    }

    @Override
    public List<String> findNearbyStores(double longitude, double latitude, double radiusKm) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(longitude, latitude),
                new Distance(radiusKm, RedisGeoCommands.DistanceUnit.KILOMETERS)
        );
        if (results == null) return List.of();
        return results.getContent().stream()
                .map(r -> r.getContent().getName())
                .toList();
    }
}
