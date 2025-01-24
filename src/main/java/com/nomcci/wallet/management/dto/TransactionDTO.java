package com.nomcci.wallet.management.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class TransactionDTO {
    private Instant timestamp;
    private String destinationWallet;
    private String transactionType;
    private BigDecimal amount = BigDecimal.ZERO;

    // Constructor
    public TransactionDTO(Instant timestamp, String destinationWallet, String transactionType, BigDecimal amount) {
        this.timestamp = timestamp;
        this.destinationWallet = destinationWallet;
        this.transactionType = transactionType;
        this.amount = amount;
    }
}
