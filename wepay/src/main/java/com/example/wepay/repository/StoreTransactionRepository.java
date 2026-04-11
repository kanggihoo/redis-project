package com.example.wepay.repository;

import com.example.wepay.domain.StoreTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreTransactionRepository extends JpaRepository<StoreTransaction, Long> {

    @Modifying
    @Query(value = "INSERT INTO store_transactions (store_id, tx_count, updated_at) VALUES (:storeId, :delta, NOW()) ON CONFLICT (store_id) DO UPDATE SET tx_count = store_transactions.tx_count + :delta, updated_at = NOW()", nativeQuery = true)
    void upsertCount(@Param("storeId") Long storeId, @Param("delta") Long delta);
}
