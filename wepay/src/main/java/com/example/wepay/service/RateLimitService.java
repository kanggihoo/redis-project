package com.example.wepay.service;

public interface RateLimitService {
    boolean isAllowed(String apiKey, String identifier);
}
