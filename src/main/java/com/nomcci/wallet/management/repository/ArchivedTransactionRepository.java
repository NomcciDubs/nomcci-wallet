package com.nomcci.wallet.management.repository;

import com.nomcci.wallet.management.model.ArchivedTransaction;
import com.nomcci.wallet.management.model.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface ArchivedTransactionRepository extends JpaRepository<ArchivedTransaction, Long> {
    Page<ArchivedTransaction> findByWalletAndTimestampBetween(Wallet wallet, Instant startTimestamp, Instant endTimestamp, Pageable pageable);
}
