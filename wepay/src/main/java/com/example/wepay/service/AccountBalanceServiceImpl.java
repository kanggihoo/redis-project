package com.example.wepay.service;

import com.example.wepay.event.AccountCacheEvictEvent;
import com.example.wepay.repository.AccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountBalanceServiceImpl implements AccountBalanceService {

    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    AccountBalanceServiceImpl(AccountRepository accountRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void updateBalance(Long accountId, Long newBalance) {
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        account.updateBalance(newBalance);
        accountRepository.save(account);

        // ✅ FIX: 캐시 삭제를 직접 호출하지 않고 이벤트를 발행한다.
        // AccountCacheEvictListener가 @TransactionalEventListener(AFTER_COMMIT) 으로
        // 커밋 완료 후에만 캐시를 삭제한다 → 롤백 시 캐시 보존
        eventPublisher.publishEvent(new AccountCacheEvictEvent(accountId));
    }
}
