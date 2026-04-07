package com.example.wepay.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_transactions")
public class StoreTransaction {

    @Id
    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "tx_count", nullable = false)
    private Long txCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected StoreTransaction() {}

    public StoreTransaction(Long storeId, Long txCount) {
        this.storeId = storeId;
        this.txCount = txCount;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getStoreId() { return storeId; }
    public Long getTxCount() { return txCount; }

    public void addCount(Long delta) {
        this.txCount += delta;
        this.updatedAt = LocalDateTime.now();
    }
}
