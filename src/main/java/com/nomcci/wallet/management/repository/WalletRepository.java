package com.nomcci.wallet.management.repository;

import com.nomcci.wallet.management.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);


    boolean existsByUserId(Long userId);
}
