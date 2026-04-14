package com.example.wepay.controller;

import com.example.wepay.domain.Account;
import com.example.wepay.service.AccountCacheService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final Map<String, AccountCacheService> strategies;

    public AccountController(
            @Qualifier("accountCacheServiceImpl") AccountCacheService baseline,
            @Qualifier("mutexLock") AccountCacheService mutexLock,
            @Qualifier("logicalExpiration") AccountCacheService logicalExpiration,
            @Qualifier("ttlJitter") AccountCacheService ttlJitter) {
        this.strategies = Map.of(
                "baseline", baseline,
                "mutexLock", mutexLock,
                "logicalExpiration", logicalExpiration,
                "ttlJitter", ttlJitter
        );
    }

    @GetMapping("/{id}")
    public Account getAccount(@PathVariable Long id,
                              @RequestParam(defaultValue = "baseline") String strategy) {
        return strategies.getOrDefault(strategy, strategies.get("baseline")).getAccount(id);
    }
}
