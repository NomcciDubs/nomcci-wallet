package com.nomcci.wallet.management.repository;
import com.nomcci.wallet.management.model.ArchivedTransaction;
import com.nomcci.wallet.management.model.TransactionSummary;
import com.nomcci.wallet.management.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionSummaryRepository extends JpaRepository<TransactionSummary, Long> {
    List<TransactionSummary> findByWallet(Wallet wallet);
}
