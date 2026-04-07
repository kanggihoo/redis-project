package com.example.wepay.event;

import com.example.wepay.service.AccountCacheService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AccountCacheEvictListener {

    private final AccountCacheService accountCacheService;

    public AccountCacheEvictListener(AccountCacheService accountCacheService) {
        this.accountCacheService = accountCacheService;
    }

    /**
     * 트랜잭션 커밋 완료 후에만 캐시를 삭제한다.
     * 롤백 시에는 이 메서드가 호출되지 않으므로 캐시가 보존된다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAfterCommit(AccountCacheEvictEvent event) {
        accountCacheService.evictAccount(event.accountId());
    }
}
