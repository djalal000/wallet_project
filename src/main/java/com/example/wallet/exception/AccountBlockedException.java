package com.example.wallet.exception;

public class AccountBlockedException extends RuntimeException {

    public AccountBlockedException(Long accountId) {
        super("Account is blocked: " + accountId);
    }
}