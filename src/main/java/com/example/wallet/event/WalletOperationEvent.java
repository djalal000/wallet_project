package com.example.wallet.event;

import java.math.BigDecimal;

public record WalletOperationEvent(
        Long transactionId,
        String type,
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount
) {
}