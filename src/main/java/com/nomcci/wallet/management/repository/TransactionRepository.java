package com.nomcci.wallet.management.repository;

import com.nomcci.wallet.management.model.Transaction;
import com.nomcci.wallet.management.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByWallet(Wallet wallet);
    Page<Transaction> findByWalletAndTimestampBetween(
            Wallet wallet,
            Instant startTimestamp,
            Instant endTimestamp,
            Pageable pageable
    );
    @Query("SELECT t FROM Transaction t WHERE t.wallet = :wallet AND t.timestamp < :cutoffDate")
    List<Transaction> findOldTransactions(@Param("wallet") Wallet wallet, @Param("cutoffDate") Instant cutoffDate);

    @Query("SELECT t FROM Transaction t WHERE t.wallet = :wallet AND t.timestamp >= :cutoffDate")
    List<Transaction> findRecentTransactions(@Param("wallet") Wallet wallet, @Param("cutoffDate") Instant cutoffDate);
}
