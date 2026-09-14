package com.example.wallet.account;

import com.example.wallet.transaction.Transaction;
import com.example.wallet.transaction.TransactionRepository;
import com.example.wallet.transaction.TransactionStatus;
import com.example.wallet.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.wallet.exception.AccountBlockedException;
import com.example.wallet.exception.AccountNotFoundException;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public void deposit(Long accountId, BigDecimal amount) {

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (account.getStatus() == AccountStatus.BLOCKED) {
            throw new AccountBlockedException(accountId);
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        account.setBalance(account.getBalance().add(amount));

        Transaction transaction = new Transaction(
                null,
                account,
                amount,
                TransactionType.DEPOSIT,
                TransactionStatus.SUCCESS,
                Instant.now()
        );

        transactionRepository.save(transaction);
    }
}