package com.nomcci.wallet.management.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
public class PaymentVerificationResponseDTO {
    private String status;
    private BigDecimal amount;
    private String currency;

    public PaymentVerificationResponseDTO(String status, BigDecimal amount, String currency) {
        this.status = status;
        this.amount = amount;
        this.currency = currency;
    }
}
