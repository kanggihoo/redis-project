package com.example.wepay.cache;

public record CacheWrapper<T>(T data, long expireAt) {
    public boolean isExpired() { return System.currentTimeMillis() > expireAt; }
}
