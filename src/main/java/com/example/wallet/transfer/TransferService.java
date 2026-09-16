package com.example.wallet.transfer;

import com.example.wallet.account.Account;
import com.example.wallet.account.AccountRepository;
import com.example.wallet.account.AccountStatus;
import com.example.wallet.event.WalletOperationEvent;
import com.example.wallet.exception.AccountBlockedException;
import com.example.wallet.exception.AccountNotFoundException;
import com.example.wallet.exception.ForbiddenOperationException;
import com.example.wallet.exception.InsufficientBalanceException;
import com.example.wallet.transaction.Transaction;
import com.example.wallet.transaction.TransactionRepository;
import com.example.wallet.transaction.TransactionStatus;
import com.example.wallet.transaction.TransactionType;
import com.example.wallet.transaction.WalletTransactionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final WalletTransactionService walletTransactionService;

    public TransferService(
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
    public void transfer(
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount) {

        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException(
                    "Source and destination accounts must be different"
            );
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero"
            );
        }

        // Always lock accounts in the same order.
        Long firstAccountId =
                Math.min(sourceAccountId, destinationAccountId);

        Long secondAccountId =
                Math.max(sourceAccountId, destinationAccountId);

        Account firstAccount = accountRepository
                .findByIdForUpdate(firstAccountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(firstAccountId)
                );

        Account secondAccount = accountRepository
                .findByIdForUpdate(secondAccountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(secondAccountId)
                );

        Account sourceAccount =
                sourceAccountId.equals(firstAccountId)
                        ? firstAccount
                        : secondAccount;

        Account destinationAccount =
                destinationAccountId.equals(firstAccountId)
                        ? firstAccount
                        : secondAccount;

        verifyAccountOwnership(sourceAccount);

        if (sourceAccount.getStatus() == AccountStatus.BLOCKED) {

            walletTransactionService.recordFailedTransaction(
                    sourceAccount,
                    destinationAccount,
                    amount,
                    TransactionType.TRANSFER
            );

            throw new AccountBlockedException(sourceAccountId);
        }

        if (destinationAccount.getStatus() == AccountStatus.BLOCKED) {

            walletTransactionService.recordFailedTransaction(
                    sourceAccount,
                    destinationAccount,
                    amount,
                    TransactionType.TRANSFER
            );

            throw new AccountBlockedException(destinationAccountId);
        }

        // Not enough money: record the failed transaction.
        if (sourceAccount.getBalance().compareTo(amount) < 0) {

            walletTransactionService.recordFailedTransaction(
                    sourceAccount,
                    destinationAccount,
                    amount,
                    TransactionType.TRANSFER
            );

            throw new InsufficientBalanceException();
        }

        // Remove money from source account.
        sourceAccount.setBalance(
                sourceAccount.getBalance().subtract(amount)
        );

        // Add money to destination account.
        destinationAccount.setBalance(
                destinationAccount.getBalance().add(amount)
        );

        // Record the successful transfer.
        Transaction transaction = new Transaction(
                sourceAccount,
                destinationAccount,
                amount,
                TransactionType.TRANSFER,
                TransactionStatus.SUCCESS,
                Instant.now()
        );

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        // Trigger deferred audit/notification after successful commit.
        eventPublisher.publishEvent(
                new WalletOperationEvent(
                        savedTransaction.getId(),
                        "TRANSFER",
                        sourceAccount.getId(),
                        destinationAccount.getId(),
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
}