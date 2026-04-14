package com.example.wepay.cache;

import com.example.wepay.domain.Account;

/**
 * CacheWrapper<Account>의 구체 클래스.
 * GenericJacksonJsonRedisSerializer + DefaultTyping.NON_FINAL 환경에서
 * 제네릭 record는 역직렬화 시 타입 소거 문제가 발생하므로 구체 클래스로 대체한다.
 */
public class AccountCacheWrapper {

    private Account data;
    private long expireAt;

    protected AccountCacheWrapper() {}

    public AccountCacheWrapper(Account data, long expireAt) {
        this.data = data;
        this.expireAt = expireAt;
    }

    public Account getData() { return data; }
    public long getExpireAt() { return expireAt; }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireAt;
    }
}
