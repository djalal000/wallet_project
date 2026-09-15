package com.example.wallet.account.dto;

import com.example.wallet.account.Account;
import com.example.wallet.account.AccountStatus;

import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        BigDecimal balance,
        AccountStatus status
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getBalance(),
                account.getStatus()
        );
    }
}