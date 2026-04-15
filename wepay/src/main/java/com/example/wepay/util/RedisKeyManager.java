package com.example.wepay.util;

/**
 * Redis 키 네이밍 규칙을 중앙 관리하는 유틸리티 클래스 (Phase 3 대응)
 */
public final class RedisKeyManager {

    private RedisKeyManager() {
        // 인스턴스화 방지
    }

    // 접두사 상수
    public static final String RATE_LIMIT_PREFIX = "ratelimit";
    public static final String BLACKLIST_PREFIX = "blacklist";

    /**
     * 레이트 리밋 키 생성: ratelimit:{type}:{api}:{identifier}
     */
    public static String getRateLimitKey(String type, String api, String identifier) {
        return String.format("%s:%s:%s:%s", RATE_LIMIT_PREFIX, type, api, identifier);
    }

    /**
     * JWT 블랙리스트 키 생성: blacklist:jwt:{jti}
     */
    public static String getJwtBlacklistKey(String jti) {
        return String.format("%s:jwt:%s", BLACKLIST_PREFIX, jti);
    }
}
