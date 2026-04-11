package com.example.wepay.controller;

import com.example.wepay.domain.Account;
import com.example.wepay.service.AccountCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean(name = "accountCacheServiceImpl")
    AccountCacheService baseline;

    @MockitoBean(name = "mutexLock")
    AccountCacheService mutexLock;

    @MockitoBean(name = "logicalExpiration")
    AccountCacheService logicalExpiration;

    @MockitoBean(name = "ttlJitter")
    AccountCacheService ttlJitter;

    @Test
    @DisplayName("[baseline] strategy=baseline 으로 조회하면 Account JSON 응답")
    void getAccount_baseline_returnsAccount() {
        Account account = new Account("Alice", 1_000_000L);
        given(baseline.getAccount(1L)).willReturn(account);

        assertThat(mvc.get().uri("/api/accounts/1").param("strategy", "baseline"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.ownerName").isEqualTo("Alice");
    }

    @Test
    @DisplayName("[mutexLock] strategy=mutexLock 으로 조회하면 Account JSON 응답")
    void getAccount_mutexLock_returnsAccount() {
        Account account = new Account("Bob", 2_000_000L);
        given(mutexLock.getAccount(1L)).willReturn(account);

        assertThat(mvc.get().uri("/api/accounts/1").param("strategy", "mutexLock"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.ownerName").isEqualTo("Bob");
    }

    @Test
    @DisplayName("[logicalExpiration] strategy=logicalExpiration 으로 조회하면 Account JSON 응답")
    void getAccount_logicalExpiration_returnsAccount() {
        Account account = new Account("Charlie", 3_000_000L);
        given(logicalExpiration.getAccount(1L)).willReturn(account);

        assertThat(mvc.get().uri("/api/accounts/1").param("strategy", "logicalExpiration"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.ownerName").isEqualTo("Charlie");
    }

    @Test
    @DisplayName("[ttlJitter] strategy=ttlJitter 으로 조회하면 Account JSON 응답")
    void getAccount_ttlJitter_returnsAccount() {
        Account account = new Account("Dave", 4_000_000L);
        given(ttlJitter.getAccount(1L)).willReturn(account);

        assertThat(mvc.get().uri("/api/accounts/1").param("strategy", "ttlJitter"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.ownerName").isEqualTo("Dave");
    }

    @Test
    @DisplayName("[default] strategy 파라미터 없으면 baseline 전략 사용")
    void getAccount_noStrategy_usesBaseline() {
        Account account = new Account("Eve", 5_000_000L);
        given(baseline.getAccount(1L)).willReturn(account);

        assertThat(mvc.get().uri("/api/accounts/1"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.ownerName").isEqualTo("Eve");
    }
}
