package com.nomcci.wallet.management.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction_summaries")
@Data
public class TransactionSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(nullable = false)
    private BigDecimal totalAmount; // Monto total del período resumido

    @Column(nullable = false)
    private Instant startDate; // Inicio del período
    @Column(nullable = false)
    private Instant endDate; // Fin del período
}
