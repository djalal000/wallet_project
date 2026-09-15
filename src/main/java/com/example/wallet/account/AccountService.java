package com.example.wallet.account;

import com.example.wallet.exception.AccountBlockedException;
import com.example.wallet.exception.AccountNotFoundException;
import com.example.wallet.exception.ForbiddenOperationException;
import com.example.wallet.exception.InsufficientBalanceException;
import com.example.wallet.transaction.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.wallet.event.WalletOperationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final WalletTransactionService walletTransactionService;
    public AccountService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            ApplicationEventPublisher eventPublisher,
            WalletTransactionService walletTransactionService) {

        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
        this.walletTransactionService = walletTransactionService;
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

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        eventPublisher.publishEvent(
                new WalletOperationEvent(
                        savedTransaction.getId(),
                        "DEPOSIT",
                        null,
                        account.getId(),
                        amount
                )
        );
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

            walletTransactionService.recordFailedWithdrawal(
                    account,
                    amount
            );

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

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        eventPublisher.publishEvent(
                new WalletOperationEvent(
                        savedTransaction.getId(),
                        "WITHDRAW",
                        account.getId(),
                        null,
                        amount
                )
        );
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