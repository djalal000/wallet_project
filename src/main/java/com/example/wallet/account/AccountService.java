package com.example.wallet.account;

import com.example.wallet.exception.AccountBlockedException;
import com.example.wallet.exception.AccountNotFoundException;
import com.example.wallet.exception.ForbiddenOperationException;
import com.example.wallet.exception.InsufficientBalanceException;
import com.example.wallet.transaction.Transaction;
import com.example.wallet.transaction.TransactionRepository;
import com.example.wallet.transaction.TransactionStatus;
import com.example.wallet.transaction.TransactionType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

        verifyAccountOwnership(account);

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

    @Transactional
    public void withdraw(Long accountId, BigDecimal amount) {

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        verifyAccountOwnership(account);

        if (account.getStatus() == AccountStatus.BLOCKED) {
            throw new AccountBlockedException(accountId);
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException();
        }

        account.setBalance(account.getBalance().subtract(amount));

        Transaction transaction = new Transaction(
                account,
                null,
                amount,
                TransactionType.WITHDRAW,
                TransactionStatus.SUCCESS,
                Instant.now()
        );

        transactionRepository.save(transaction);
    }

    private void verifyAccountOwnership(Account account) {

        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        if (!account.getUser().getUsername().equals(username)) {
            throw new ForbiddenOperationException(
                    "You can only operate on your own account"
            );
        }
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }



}